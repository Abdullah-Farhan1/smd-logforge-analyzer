import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

public class LogForge {
    private static final int field_count = 5;
    private static final int timestamp_field_index = 0;
    private static final int service_field_index = 1;
    private static final int level_field_index = 2;
    private static final int request_id_field_index = 3;
    private static final int message_field_index = 4;
    private static final int timestamp_length = 19;
    private static final int initial_capacity = 5;
    private static final int incident_window_seconds = 60;
    private static final int incident_min_errors = 3;
    private static final DateTimeFormatter timestamp_formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java LogForge <logfile>");
            return;
        }
        String filename = args[0];
        String outputFilename = (args.length >= 2) ? args[1] : "logforge_report.txt";

        int totalLines = 0;
        int validRecords = 0;
        int invalidRecords = 0;
        int infoCount = 0;
        int warnCount = 0;
        int errorCount = 0;

        LogEntry[] logEntries = new LogEntry[initial_capacity];
        int entryCount = 0;

        ServiceStats[] serviceStats = new ServiceStats[initial_capacity];
        int serviceCount = 0;

        String[] trackServiceNames = new String[initial_capacity];
        String[] groupFirstTimestamp = new String[initial_capacity];
        String[] groupLastTimestamp = new String[initial_capacity];
        int[] groupCount = new int[initial_capacity];
        int trackCount = 0;

        Incident[] incidents = new Incident[initial_capacity];
        int incidentCount = 0;

        RequestStats[] requestStats = new RequestStats[initial_capacity];
        int requestCount = 0;

        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new File(filename));

            // ==========================================
            // PHASE 1: READ, VALIDATE, AND STORE
            // ==========================================
            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();
                totalLines++;
                String[] fields = splitFields(line);

                if (fields == null || !isValidRecord(fields)) {
                    invalidRecords++;
                    continue;
                }
                validRecords++;

                String timestamp = fields[timestamp_field_index];
                String service = fields[service_field_index];
                String level = fields[level_field_index];
                int requestId = Integer.parseInt(fields[request_id_field_index]);
                String message = fields[message_field_index];

                if (entryCount == logEntries.length) {
                    logEntries = growEntryArray(logEntries);
                }
                logEntries[entryCount] = new LogEntry(timestamp, service, level, requestId, message);
                entryCount++;
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error: could not open file '" + filename + "'");
            return;
        } finally {
            if (fileScanner != null) {
                fileScanner.close();
            }
        }

        // ==========================================
        // PHASE 2: SORTING (Q8)
        // ==========================================
        for (int i = 1; i < entryCount; i++) {
            for (int j = i; j > 0 && logEntries[j - 1].getTimestamp().compareTo(logEntries[j].getTimestamp()) >= 0; j--) {
                LogEntry temp_swap_buffer = logEntries[j];
                logEntries[j] = logEntries[j - 1];
                logEntries[j - 1] = temp_swap_buffer;
            }
        }

        // ==========================================
        // PHASE 3: ANALYSIS ON SORTED ARRAY (Q4-Q7)
        // ==========================================
        for (int i = 0; i < entryCount; i++) {
            LogEntry entry = logEntries[i];

            String timestamp = entry.getTimestamp();
            String service = entry.getService();
            String level = entry.getLevel();
            int requestId = entry.getRequestId();

            if (level.equals("INFO")) infoCount++;
            else if (level.equals("WARN")) warnCount++;
            else if (level.equals("ERROR")) errorCount++;

            int serviceIndex = findServiceIndex(serviceStats, serviceCount, service);
            if (serviceIndex == -1) {
                if (serviceCount == serviceStats.length) {
                    serviceStats = growServiceArray(serviceStats);
                }
                serviceStats[serviceCount] = new ServiceStats(service);
                serviceIndex = serviceCount;
                serviceCount++;
            }
            serviceStats[serviceIndex].addRecord(level);

            int requestIndex = findRequestIndex(requestStats, requestCount, requestId);
            if (requestIndex == -1) {
                if (requestCount == requestStats.length) {
                    requestStats = growRequestArray(requestStats);
                }
                requestStats[requestCount] = new RequestStats(requestId);
                requestIndex = requestCount;
                requestCount++;
            }
            requestStats[requestIndex].addRecord(service, level);

            if (level.equals("ERROR")) {
                int trackIndex = findTrackIndex(trackServiceNames, trackCount, service);

                if (trackIndex == -1) {
                    if (trackCount == trackServiceNames.length) {
                        trackServiceNames = growStringArray(trackServiceNames);
                        groupFirstTimestamp = growStringArray(groupFirstTimestamp);
                        groupLastTimestamp = growStringArray(groupLastTimestamp);
                        groupCount = growIntArray(groupCount);
                    }
                    trackServiceNames[trackCount] = service;
                    groupFirstTimestamp[trackCount] = timestamp;
                    groupLastTimestamp[trackCount] = timestamp;
                    groupCount[trackCount] = 1;
                    trackCount++;
                } else {
                    LocalDateTime groupFirstTime = LocalDateTime.parse(groupFirstTimestamp[trackIndex], timestamp_formatter);
                    LocalDateTime currentTime = LocalDateTime.parse(timestamp, timestamp_formatter);
                    long secondsFromGroupStart = ChronoUnit.SECONDS.between(groupFirstTime, currentTime);

                    if (secondsFromGroupStart <= incident_window_seconds) {
                        groupCount[trackIndex]++;
                        groupLastTimestamp[trackIndex] = timestamp;
                    } else {
                        if (groupCount[trackIndex] >= incident_min_errors) {
                            if (incidentCount == incidents.length) {
                                incidents = growIncidentArray(incidents);
                            }
                            incidents[incidentCount] = new Incident(trackServiceNames[trackIndex],
                                    groupFirstTimestamp[trackIndex], groupLastTimestamp[trackIndex]);
                            incidentCount++;
                        }
                        groupFirstTimestamp[trackIndex] = timestamp;
                        groupLastTimestamp[trackIndex] = timestamp;
                        groupCount[trackIndex] = 1;
                    }
                }
            }
        }

        // Q6: flush any groups still open at end of file
        for (int i = 0; i < trackCount; i++) {
            if (groupCount[i] >= incident_min_errors) {
                if (incidentCount == incidents.length) {
                    incidents = growIncidentArray(incidents);
                }
                incidents[incidentCount] = new Incident(trackServiceNames[i], groupFirstTimestamp[i], groupLastTimestamp[i]);
                incidentCount++;
            }
        }

        sortServicesByErrorRateDescending(serviceStats, serviceCount);

        String report = buildReport(totalLines, validRecords, invalidRecords,
                infoCount, warnCount, errorCount,
                serviceStats, serviceCount,
                incidents, incidentCount,
                requestStats, requestCount);

        System.out.print(report);

        try {
            PrintWriter reportWriter = new PrintWriter(new FileWriter(outputFilename));
            reportWriter.print(report);
            reportWriter.close();
        } catch (IOException e) {
            System.err.println("Error: could not write report to '" + outputFilename + "'");
        }
    }

    // Q9: builds the full report text, applying the 5 required
    // deviations from format.txt's base structure
    private static String buildReport(int totalLines, int validRecords, int invalidRecords,
            int infoCount, int warnCount, int errorCount,
            ServiceStats[] serviceStats, int serviceCount,
            Incident[] incidents, int incidentCount,
            RequestStats[] requestStats, int requestCount) {

        String report = "";
        report += "========================\n";
        report += "LOGFORGE INCIDENT REPORT\n";
        report += "========================\n";
        report += "\n";
        report += "\n";
        report += "1. SUMMARY\n";
        report += "----------\n";
        report += "\n";
        report += "Total lines: " + totalLines + "\n";
        report += "Valid records: " + validRecords + "\n";
        report += "Invalid records: " + invalidRecords + "\n";
        report += "\n";
        report += "ERROR: " + errorCount + "\n";
        report += "INFO: " + infoCount + "\n";
        report += "WARN: " + warnCount + "\n";
        report += "\n";
        report += "\n";
        report += "2. SERVICE STATISTICS\n";
        report += "---------------------\n";
        report += "\n";

        for (int i = 0; i < serviceCount; i++) {
            ServiceStats s = serviceStats[i];
            report += "Service: " + s.getServiceName() + "\n";
            report += "Total: " + s.getTotal() + "\n";
            report += "INFO: " + s.getInfoCount() + "\n";
            report += "WARN: " + s.getWarnCount() + "\n";
            report += "ERROR: " + s.getErrorCount() + "\n";
            report += "Error Rate: " + s.getErrorRatePercentString5() + "\n";
            report += "\n";
        }

        report += "\n";
        report += "3. INCIDENTS\n";
        report += "------------\n";
        report += "\n";

        if (incidentCount == 0) {
            report += "No incidents detected.\n";
        } else {
            for (int i = 0; i < incidentCount; i++) {
                Incident inc = incidents[i];
                report += "Service: " + inc.getServiceName() + "\n";
                report += "First Error: " + extractTimeOnly(inc.getFirstErrorTimestamp()) + "\n";
                report += "Last Error: " + extractTimeOnly(inc.getLastErrorTimestamp()) + "\n";
                report += "\n";
            }
        }

        report += "\n";
        report += "4. REQUEST STATISTICS\n";
        report += "---------------------\n";
        report += "\n";

        for (int i = 0; i < requestCount; i++) {
            RequestStats r = requestStats[i];
            String status = r.isFailed() ? "FAILED" : "SUCCESS";
            report += "Request " + r.getRequestId() + " : " + status + "\n";
            report += "Records: " + r.getTotalRecords() + "\n";
            report += "Errors: " + r.getErrorCount() + "\n";
            report += "Services: " + r.getServicesString() + "\n";
            report += "\n";
        }

        report += "\n";
        report += "eod\n";
        return report;
    }

    // extracts just the HH:MM:SS portion from a full 19-char timestamp
    private static String extractTimeOnly(String timestamp) {
        return timestamp.substring(11);
    }

    private static RequestStats[] growRequestArray(RequestStats[] array) {
        RequestStats[] newArray = new RequestStats[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static int findRequestIndex(RequestStats[] array, int count, int requestId) {
        for (int i = 0; i < count; i++) {
            if (array[i].matchesRequest(requestId)) {
                return i;
            }
        }
        return -1;
    }

    private static LogEntry[] growEntryArray(LogEntry[] array) {
        LogEntry[] newArray = new LogEntry[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static ServiceStats[] growServiceArray(ServiceStats[] array) {
        ServiceStats[] newArray = new ServiceStats[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static Incident[] growIncidentArray(Incident[] array) {
        Incident[] newArray = new Incident[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static String[] growStringArray(String[] array) {
        String[] newArray = new String[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static int[] growIntArray(int[] array) {
        int[] newArray = new int[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    private static int findServiceIndex(ServiceStats[] array, int count, String serviceName) {
        for (int i = 0; i < count; i++) {
            if (array[i].matchesService(serviceName)) {
                return i;
            }
        }
        return -1;
    }

    private static int findTrackIndex(String[] array, int count, String serviceName) {
        for (int i = 0; i < count; i++) {
            if (array[i].equals(serviceName)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitFields(String line) {
        String[] fields = new String[field_count];
        int fieldIndex = 0;
        int start = 0;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '|') {
                if (fieldIndex >= field_count - 1) {
                    return null;
                }
                fields[fieldIndex] = line.substring(start, i);
                fieldIndex++;
                start = i + 1;
            }
        }

        if (fieldIndex != field_count - 1) {
            return null;
        }

        fields[fieldIndex] = line.substring(start);
        return fields;
    }

    private static boolean isValidRecord(String[] fields) {
        String timestamp = fields[timestamp_field_index];
        String level = fields[level_field_index];
        String requestId = fields[request_id_field_index];

        return isValidTimestamp(timestamp) && isValidLevel(level) && isValidRequestId(requestId);
    }

    private static boolean isValidLevel(String level) {
        return level.equals("INFO") || level.equals("WARN") || level.equals("ERROR");
    }

    private static boolean isValidRequestId(String requestId) {
        if (requestId == null || requestId.length() == 0) {
            return false;
        }

        for (int i = 0; i < requestId.length(); i++) {
            char c = requestId.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }

        try {
            int value = Integer.parseInt(requestId);
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidTimestamp(String timestamp) {
        if (timestamp == null || timestamp.length() != timestamp_length) {
            return false;
        }

        for (int i = 0; i < timestamp_length; i++) {
            char c = timestamp.charAt(i);

            if (i == 4 || i == 7) {
                if (c != '-') return false;
            } else if (i == 10) {
                if (c != ' ') return false;
            } else if (i == 13 || i == 16) {
                if (c != ':') return false;
            } else {
                if (c < '0' || c > '9') return false;
            }
        }

        int month = (timestamp.charAt(5) - '0') * 10 + (timestamp.charAt(6) - '0');
        return month >= 1 && month <= 12;
    }

    private static void sortServicesByErrorRateDescending(ServiceStats[] arr, int count) {
        sortServicesByNameAscending(arr, count);

        int[] keys = new int[count];
        for (int i = 0; i < count; i++) {
            keys[i] = 10000 - arr[i].getErrorRateScaledKey();
        }

        int[] placeValues = {1, 10, 100, 1000, 10000};
        for (int p = 0; p < placeValues.length; p++) {
            radixCountingSortPass(arr, keys, count, placeValues[p]);
        }
    }

    private static void sortServicesByNameAscending(ServiceStats[] arr, int count) {
        for (int i = 0; i < count - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < count; j++) {
                if (arr[j].getServiceName().compareTo(arr[minIndex].getServiceName()) < 0) {
                    minIndex = j;
                }
            }
            if (minIndex != i) {
                ServiceStats temp_swap_buffer = arr[i];
                arr[i] = arr[minIndex];
                arr[minIndex] = temp_swap_buffer;
            }
        }
    }

    private static void radixCountingSortPass(ServiceStats[] arr, int[] keys, int count, int placeValue) {
        int[] counts = new int[12];

        for (int i = 0; i < count; i++) {
            int digit = (keys[i] / placeValue) % 10;
            counts[digit]++;
        }
        for (int d = 1; d < 10; d++) {
            counts[d] += counts[d - 1];
        }

        ServiceStats[] output = new ServiceStats[count];
        int[] keyOutput = new int[count];

        for (int i = count - 1; i >= 0; i--) {
            int digit = (keys[i] / placeValue) % 10;
            int position = counts[digit] - 1;
            ServiceStats temp_swap_buffer = arr[i];
            output[position] = temp_swap_buffer;
            keyOutput[position] = keys[i];
            counts[digit]--;
        }

        for (int i = 0; i < count; i++) {
            arr[i] = output[i];
            keys[i] = keyOutput[i];
        }
    }
}