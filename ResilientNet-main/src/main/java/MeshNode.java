import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;

/**
 * Desktop MeshNode — sends/receives JSON packets over UDP port 9876.
 *
 * KEY FIXES for phone ↔ laptop discovery:
 *  1. Self-filter no longer relies on getLocalHost() (which returns 127.0.0.1
 *     on some systems). Instead we collect ALL local interface IPs and compare.
 *  2. sendBroadcastHello() now sends to EVERY subnet found on every
 *     non-loopback interface — so it reaches phones on the same WiFi.
 *  3. A direct unicast HELLO is also sent back to any sender whose address
 *     we don't yet know — needed because Android often can't receive broadcast.
 *  4. Heartbeat interval kept at 5 s; timeout at 20 s (phone WiFi is slower).
 */
public class MeshNode {
    private static final int PORT = 9876;
    private DatagramSocket socket;
    private MeshEventListener listener;
    private final Gson gson = new Gson();

    // ===== Node State =====
    private int     batteryLevel     = 100;
    private boolean running          = true;
    private boolean isNeighborInRange = true;
    private boolean isAdmin;
    private String  nodeId;
    private String  role;

    // ===== Mesh State =====
    private Map<InetSocketAddress, Long>   neighbors    = new ConcurrentHashMap<>();
    private Map<String, InetSocketAddress> knownNodes   = new ConcurrentHashMap<>();
    private Set<String> disconnectedNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Map<String, String> nodePriorities          = new ConcurrentHashMap<>();

    private Map<String, Message> localStore = new ConcurrentHashMap<>();
    private List<Message>        buffer     = Collections.synchronizedList(new ArrayList<>());

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // ===== Cached local IPs (refreshed each heartbeat) =====
    private final Set<String> localIpAddresses = ConcurrentHashMap.newKeySet();

    // ───────────────────────────────────────────────────────────
    // JSON Packet Envelope
    // ───────────────────────────────────────────────────────────
    private static class NetworkPacket {
        PacketType type;
        String     senderId;
        Message    payload;

        NetworkPacket(PacketType type, String senderId, Message payload) {
            this.type     = type;
            this.senderId = senderId;
            this.payload  = payload;
        }
    }

    // ───────────────────────────────────────────────────────────
    // Constructor
    // ───────────────────────────────────────────────────────────
    public MeshNode(boolean adminMode, MeshEventListener listener) throws Exception {
        this.isAdmin  = adminMode;
        this.role     = adminMode ? "ADMIN" : "USER";
        this.listener = listener;
        this.nodeId   = loadOrGenerateId();

        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);
        socket.setReuseAddress(true);

        refreshLocalIps();   // populate the self-filter set immediately

        listener.onSystemMessage("MESH NODE ONLINE (" + role + ") | ID: " + nodeId
                + " | Port: " + PORT);

        startDiscoveryHeartbeat();
        startTimeoutChecker();
    }

    // ───────────────────────────────────────────────────────────
    // Public Accessors
    // ───────────────────────────────────────────────────────────
    public String  getNodeId()       { return nodeId; }
    public String  getRole()         { return role; }
    public boolean isAdmin()         { return isAdmin; }
    public int     getBatteryLevel() { return batteryLevel; }

    public Set<String> getKnownNodeIds() {
        return Collections.unmodifiableSet(knownNodes.keySet());
    }

    public void setBatteryLevel(int level) {
        this.batteryLevel = Math.max(0, Math.min(100, level));
        listener.onBatteryUpdate(this.batteryLevel);
        listener.onSystemMessage("Battery set to " + this.batteryLevel + "%");
    }

    public void setNodePriority(String nodeId, String priority) {
        nodePriorities.put(nodeId, priority);
        fireDashboardUpdate();
    }

    // ───────────────────────────────────────────────────────────
    // ID Persistence
    // ───────────────────────────────────────────────────────────
    private String loadOrGenerateId() {
        File file = new File("node_id_" + PORT + ".txt");
        try {
            if (file.exists()) {
                try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                    return r.readLine();
                }
            } else {
                String newId = UUID.randomUUID().toString().substring(0, 6);
                try (FileWriter w = new FileWriter(file)) { w.write(newId); }
                return newId;
            }
        } catch (IOException e) {
            return "NODE_" + (int)(Math.random() * 1000);
        }
    }

    // ───────────────────────────────────────────────────────────
    // Local IP helpers  (FIX #1: robust self-filter)
    // ───────────────────────────────────────────────────────────
    private void refreshLocalIps() {
        localIpAddresses.clear();
        localIpAddresses.add("127.0.0.1");
        localIpAddresses.add("0.0.0.0");
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return;
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        localIpAddresses.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /** Returns true if this packet came from one of our own interfaces on PORT */
    private boolean isSelfPacket(DatagramPacket packet) {
        return localIpAddresses.contains(packet.getAddress().getHostAddress())
               && packet.getPort() == PORT;
    }

    // ───────────────────────────────────────────────────────────
    // Scheduled Tasks
    // ───────────────────────────────────────────────────────────
    private void startDiscoveryHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                refreshLocalIps();          // keep self-filter fresh
                if (isNeighborInRange) sendBroadcastHello();
            } catch (Exception ignored) {}
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void startTimeoutChecker() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            neighbors.entrySet().removeIf(entry -> {
                boolean timedOut = (now - entry.getValue()) > 60_000; // 20 s for phone WiFi
                if (timedOut) {
                    String timedOutId = null;
                    for (Map.Entry<String, InetSocketAddress> known : knownNodes.entrySet()) {
                        if (known.getValue().equals(entry.getKey())) {
                            timedOutId = known.getKey();
                            break;
                        }
                    }
                    if (timedOutId != null) {
                        disconnectedNodes.add(timedOutId);
                        listener.onPeerLost(timedOutId);
                        if (isAdmin)
                            listener.onSystemMessage("[ADMIN ALERT] Node Disconnected: " + timedOutId);
                        fireDashboardUpdate();
                    }
                }
                return timedOut;
            });
        }, 5, 5, TimeUnit.SECONDS);
    }

    // ───────────────────────────────────────────────────────────
    // Listening
    // ───────────────────────────────────────────────────────────
    public void startListening() {
        new Thread(() -> {
            byte[] buf = new byte[8192];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    // FIX #1: use multi-interface self check, not getLocalHost()
                    if (isSelfPacket(packet)) continue;

                    InetSocketAddress sender = new InetSocketAddress(
                            packet.getAddress(), packet.getPort());

                    String jsonString = new String(
                            packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    NetworkPacket netPacket = gson.fromJson(jsonString, NetworkPacket.class);
                    if (netPacket == null || netPacket.senderId == null) continue;

                    PacketType type     = netPacket.type;
                    String     senderId = netPacket.senderId;

                    boolean wasOffline = disconnectedNodes.remove(senderId);
                    boolean isNew      = !knownNodes.containsKey(senderId);

                    if (wasOffline) {
                        listener.onPeerReturned(senderId);
                        if (!knownNodes.containsKey(senderId)) listener.onPeerDiscovered(senderId);
                    } else if (isNew) {
                        listener.onPeerDiscovered(senderId);
                    }

                    neighbors.put(sender, System.currentTimeMillis());
                    knownNodes.put(senderId, sender);

                    // FIX #3: reply with a unicast HELLO so the phone can hear us
                    // even if it never received our broadcast
                    if (isNew || wasOffline) {
                        sendObject(PacketType.HELLO, null, sender);
                    }

                    if (type == PacketType.DATA && netPacket.payload != null) {
                        Message msg = netPacket.payload;
                        sendObject(PacketType.ACK, null, sender);
                        handleIncomingMessage(msg);
                    } else if (type == PacketType.ACK) {
                        listener.onDeliveryAck(senderId);
                    }

                    fireDashboardUpdate();

                } catch (Exception ignored) {}
            }
        }, "mesh-listener").start();
    }

    // ───────────────────────────────────────────────────────────
    // Message Handling
    // ───────────────────────────────────────────────────────────
    private void handleIncomingMessage(Message msg) {
        if (localStore.containsKey(msg.id)) return;
        localStore.put(msg.id, msg);

        boolean forMe = msg.scope == Message.Scope.BROADCAST
                || (msg.scope == Message.Scope.TARGETED
                    && (msg.targets.contains(nodeId) || isAdmin));

        if (forMe) {
            if (isAdmin && msg.urgent) {
                DateTimeFormatter dtf = DateTimeFormatter
                        .ofPattern("EEE, d MMM yyyy HH:mm:ss Z")
                        .withZone(ZoneId.of("Asia/Kolkata"));
                listener.onSystemMessage("[" + dtf.format(Instant.ofEpochMilli(msg.timestamp))
                        + "] [SOS ALERT] From " + msg.senderId + ": " + msg.content);
            }
            listener.onMessageReceived(msg);
        }

        if (msg.ttl > 0 && msg.scope != Message.Scope.LOCAL) {
            msg.ttl--;
            for (InetSocketAddress n : neighbors.keySet()) {
                try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
            }
        }
    }

    // ───────────────────────────────────────────────────────────
    // Public Send API
    // ───────────────────────────────────────────────────────────
    public String broadcastMessage(String content, Message.Priority priority) {
        return sendOrBuffer(new Message(content, nodeId, role,
                Message.Scope.BROADCAST, null, priority));
    }

    public String broadcastMessage(String content) {
        return broadcastMessage(content, Message.Priority.NORMAL);
    }

    public String sendTargeted(String content, Set<String> targets, Message.Priority priority) {
        if (!isAdmin) return "[DENIED] Targeted send requires Admin role.";
        return sendOrBuffer(new Message(content, nodeId, role,
                Message.Scope.TARGETED, targets, priority));
    }

    public String sendTargeted(String content, Set<String> targets) {
        return sendTargeted(content, targets, Message.Priority.NORMAL);
    }

    private String sendOrBuffer(Message msg) {
        if (batteryLevel < 15 && msg.priority != Message.Priority.CRITICAL)
            return "[BLOCKED] Battery too low (" + batteryLevel + "%). Only CRITICAL messages allowed.";

        localStore.put(msg.id, msg);

        if (!isNeighborInRange || neighbors.isEmpty()) {
            buffer.add(msg);
            listener.onBufferUpdate(buffer.size());
            return "[BUFFERED] No active neighbors. Message queued (" + buffer.size() + " in queue).";
        }

        for (InetSocketAddress n : neighbors.keySet()) {
            try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
        }
        batteryLevel = Math.max(0, batteryLevel - 1);
        listener.onBatteryUpdate(batteryLevel);
        return null;
    }

    // ───────────────────────────────────────────────────────────
    // Signal Toggle
    // ───────────────────────────────────────────────────────────
    public void toggleSignal() {
        isNeighborInRange = !isNeighborInRange;
        listener.onSignalToggled(isNeighborInRange);
        if (isNeighborInRange) flushBuffer();
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) return;
        listener.onSystemMessage("Flushing " + buffer.size() + " buffered message(s)...");
        synchronized (buffer) {
            buffer.sort((a, b) -> b.priority.ordinal() - a.priority.ordinal());
            for (Message msg : buffer) {
                for (InetSocketAddress n : neighbors.keySet()) {
                    try { sendObject(PacketType.DATA, msg, n); } catch (Exception ignored) {}
                }
                batteryLevel = Math.max(0, batteryLevel - 1);
            }
            buffer.clear();
        }
        listener.onBufferUpdate(0);
        listener.onBatteryUpdate(batteryLevel);
        listener.onSystemMessage("Buffer flushed. All messages delivered.");
    }

    // ───────────────────────────────────────────────────────────
    // Dashboard
    // ───────────────────────────────────────────────────────────
    private void fireDashboardUpdate() {
        if (!isAdmin) return;
        Map<String, String> statuses   = new LinkedHashMap<>();
        Map<String, String> priorities = new LinkedHashMap<>();
        Map<String, String> addresses  = new LinkedHashMap<>();
        knownNodes.forEach((id, addr) -> {
            statuses.put(id,   neighbors.containsKey(addr) ? "ONLINE" : "OFFLINE");
            priorities.put(id, nodePriorities.getOrDefault(id, "NORMAL"));
            addresses.put(id,  addr.getAddress().getHostAddress());
        });
        listener.onDashboardUpdate(statuses, priorities, addresses);
    }

    public String getStatusReport() {
        if (!isAdmin) return "[DENIED] Admin only.";
        if (knownNodes.isEmpty()) return "[STATUS] No known nodes yet.";
        StringBuilder sb = new StringBuilder("NODE REGISTRY\n");
        sb.append(String.format("%-10s  %-16s  %-8s  %s\n", "ID", "ADDRESS", "STATUS", "PRIORITY"));
        sb.append("─".repeat(52)).append("\n");
        knownNodes.forEach((id, addr) -> {
            String status   = neighbors.containsKey(addr) ? "ONLINE" : "OFFLINE";
            String priority = nodePriorities.getOrDefault(id, "NORMAL");
            sb.append(String.format("%-10s  %-16s  %-8s  %s\n",
                    id, addr.getAddress().getHostAddress(), status, priority));
        });
        return sb.toString();
    }

    // ───────────────────────────────────────────────────────────
    // Network Helpers  (FIX #2: broadcast on ALL active interfaces)
    // ───────────────────────────────────────────────────────────
    private void sendBroadcastHello() throws Exception {
        // 1. Limited broadcast — works on many routers
        sendObject(PacketType.HELLO, null,
                new InetSocketAddress("255.255.255.255", PORT));

        // 2. Directed broadcast on every non-loopback IPv4 interface
        //    This is the one that reaches Android phones on the same WiFi subnet
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        if (ifaces == null) return;
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) continue;
            for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                InetAddress broadcast = ia.getBroadcast();
                if (broadcast == null) continue;
                String bcastStr = broadcast.getHostAddress();
                if (bcastStr.equals("255.255.255.255")) continue; // already sent above
                sendObject(PacketType.HELLO, null,
                        new InetSocketAddress(broadcast, PORT));
            }
        }
    }

    private void sendObject(PacketType type, Message payload, InetSocketAddress target)
            throws Exception {
        NetworkPacket pkt  = new NetworkPacket(type, nodeId, payload);
        byte[]        data = gson.toJson(pkt).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(data, data.length,
                target.getAddress(), target.getPort()));
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        if (socket != null && !socket.isClosed()) socket.close();
    }
}