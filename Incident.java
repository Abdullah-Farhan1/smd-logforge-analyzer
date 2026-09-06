public class Incident {
    private final String serviceName;
    private final String firstErrorTimestamp;
    private final String lastErrorTimestamp;

    public Incident(String serviceName, String firstErrorTimestamp, String lastErrorTimestamp) {
        this.serviceName = serviceName;
        this.firstErrorTimestamp = firstErrorTimestamp;
        this.lastErrorTimestamp = lastErrorTimestamp;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getFirstErrorTimestamp() {
        return firstErrorTimestamp;
    }

    public String getLastErrorTimestamp() {
        return lastErrorTimestamp;
    }
}