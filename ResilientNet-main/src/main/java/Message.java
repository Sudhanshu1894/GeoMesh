import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Message {

    public enum Scope {
        LOCAL,      // Only for the immediate neighbor
        BROADCAST,  // For everyone in the mesh
        TARGETED    // For specific Node IDs
    }

    public enum Priority {
        LOW,      // Routine info
        NORMAL,   // Standard message
        HIGH,     // Important, needs attention
        CRITICAL  
    }

    public String   id;
    public String   senderId;
    public String   senderRole;
    public String   content;
    public boolean  urgent;
    public long     timestamp;
    public Scope    scope;
    public Set<String> targets;
    public int      ttl = 3;
    public Priority priority;

    // --- NEW: Empty Constructor Required for GSON ---
    public Message() {
        this.targets = new HashSet<>();
    }

    public Message(String content, String senderId, String senderRole,
                   Scope scope, Set<String> targets, Priority priority) {
        this.id         = UUID.randomUUID().toString();
        this.senderId   = senderId;
        this.senderRole = senderRole;
        this.content    = content;
        this.scope      = scope;
        this.targets    = (targets != null) ? targets : new HashSet<>();
        this.timestamp  = System.currentTimeMillis();
        this.priority   = (priority != null) ? priority : Priority.NORMAL;

        // Auto-escalate to CRITICAL if SOS/HELP keyword detected
        String upper = content.toUpperCase();
        this.urgent = upper.contains("HELP") || upper.contains("SOS")
                   || this.priority == Priority.CRITICAL;
        if (this.urgent && this.priority != Priority.CRITICAL) {
            this.priority = Priority.CRITICAL;
        }
    }

    // Backward-compatible constructor (no priority arg)
    public Message(String content, String senderId, String senderRole,
                   Scope scope, Set<String> targets) {
        this(content, senderId, senderRole, scope, targets, Priority.NORMAL);
    }

    @Override
    public String toString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss")
                                .withZone(ZoneId.systemDefault());
        String formattedTime = dtf.format(Instant.ofEpochMilli(this.timestamp));
        return String.format("[%s] %s: %s", formattedTime, senderId, content);
    }
}