public class RequestStats {
    private static final int initial_capacity = 5;

    private final int requestId;
    private int totalRecords;
    private int errorCount;
    private String[] services;
    private int serviceCount;

    public RequestStats(int requestId) {
        this.requestId = requestId;
        this.totalRecords = 0;
        this.errorCount = 0;
        this.services = new String[initial_capacity];
        this.serviceCount = 0;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public boolean matchesRequest(int requestId) {
        return this.requestId == requestId;
    }

    public boolean isFailed() {
        return errorCount > 0;
    }

    // Adds one valid record belonging to this request, tracking the
    // distinct services that have touched it (no duplicates, kept in
    // first-seen order).
    public void addRecord(String service, String level) {
        totalRecords++;
        if (level.equals("ERROR")) {
            errorCount++;
        }

        boolean alreadyPresent = false;
        for (int i = 0; i < serviceCount; i++) {
            if (services[i].equals(service)) {
                alreadyPresent = true;
                break;
            }
        }

        if (!alreadyPresent) {
            if (serviceCount == services.length) {
                services = growServiceNameArray(services);
            }
            services[serviceCount] = service;
            serviceCount++;
        }
    }

    private static String[] growServiceNameArray(String[] array) {
        String[] newArray = new String[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Builds a space-separated list of the distinct services, in the
    // order they were first encountered for this request
    public String getServicesString() {
        String result = "";
        for (int i = 0; i < serviceCount; i++) {
            if (i > 0) {
                result += " ";
            }
            result += services[i];
        }
        return result;
    }
}