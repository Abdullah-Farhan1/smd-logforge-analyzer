public class LogEntry {
    private final String timestamp;
    private final String service;
    private final String level;
    private final int requestId;
    private final String message;

    public LogEntry(String timestamp, String service, String level, int requestId, String message) {
        this.timestamp = timestamp;
        this.service = service;
        this.level = level;
        this.requestId = requestId;
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getService() {
        return service;
    }

    public String getLevel() {
        return level;
    }

    public int getRequestId() {
        return requestId;
    }

    public String getMessage() {
        return message;
    }

    // Required by Q3. Not called anywhere in this program per the spec.
    public boolean matchRecord(int requestId) {
        return this.requestId == requestId;
    }
}