package one.chandan.rubato.util;

public class MetadataSyncLogEntry {
    private final String message;
    private final String stage;
    private final long timestamp;
    private final boolean completed;

    public MetadataSyncLogEntry(String message, String stage, long timestamp, boolean completed) {
        this.message = message;
        this.stage = stage;
        this.timestamp = timestamp;
        this.completed = completed;
    }

    public String getMessage() {
        return message;
    }

    public String getStage() {
        return stage;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isCompleted() {
        return completed;
    }
}
