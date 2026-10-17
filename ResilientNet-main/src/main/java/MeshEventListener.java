import java.util.Map;

public interface MeshEventListener {
    void onMessageReceived(Message msg);
    void onPeerDiscovered(String peerId);
    void onPeerLost(String peerId);
    void onPeerReturned(String peerId);
    void onSystemMessage(String text);
    void onSignalToggled(boolean signalOn);
    void onBufferUpdate(int bufferedCount);
    void onDeliveryAck(String fromId);
    void onBatteryUpdate(int level);

    /**
     * Fired whenever the node registry changes so the admin dashboard can refresh.
     * nodeStatuses maps nodeId -> "ONLINE" | "OFFLINE"
     * nodePriorities maps nodeId -> priority label string
     */
    void onDashboardUpdate(Map<String, String> nodeStatuses,
                           Map<String, String> nodePriorities,
                           Map<String, String> nodeAddresses);
}