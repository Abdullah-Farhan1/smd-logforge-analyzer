public class ServiceStats {
    private final String serviceName;
    private int total;
    private int infoCount;
    private int warnCount;
    private int errorCount;

    public ServiceStats(String serviceName) {
        this.serviceName = serviceName;
        this.total = 0;
        this.infoCount = 0;
        this.warnCount = 0;
        this.errorCount = 0;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getTotal() {
        return total;
    }

    public int getInfoCount() {
        return infoCount;
    }

    public int getWarnCount() {
        return warnCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public boolean matchesService(String name) {
        return this.serviceName.equals(name);
    }

    // Called once per valid LogEntry that belongs to this service
    public void addRecord(String level) {
        total++;
        if (level.equals("INFO")) infoCount++;
        else if (level.equals("WARN")) warnCount++;
        else if (level.equals("ERROR")) errorCount++;
    }
}