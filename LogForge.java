import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;



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
        if(args.length < 1){
            System.out.println("Usage: java LogForge <logfile>");
            return;
        }
        String filename = args[0];
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

        // Q6: per-service "current group" tracking, updated as ERROR
        // records are encountered in chronological order
        String[] trackServiceNames = new String[initial_capacity];
        String[] groupFirstTimestamp = new String[initial_capacity];
        String[] groupLastTimestamp = new String[initial_capacity];
        int[] groupCount = new int[initial_capacity];
        int trackCount = 0;

        Incident[] incidents = new Incident[initial_capacity];
        int incidentCount = 0;

        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new File(filename));   
            
            while (fileScanner.hasNextLine()){
                String line = fileScanner.nextLine();
                totalLines++;
                String[] fields = splitFields(line);

                if(fields == null || !isValidRecord(fields)){
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

                if(level.equals("INFO")) infoCount++;
                else if(level.equals("WARN")) warnCount++;
                else if(level.equals("ERROR")) errorCount++;

                // Q6: fold this record into the running per-service
                // incident grouping, but only ERROR records matter
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
        
        } 
        catch (FileNotFoundException e){
                System.err.println("Error: could not open file '"+filename+"'");
                return;
        } 
        finally {
            if(fileScanner != null){
                fileScanner.close();
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
        
        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid records: " + validRecords);
        System.out.println("Invalid records: " + invalidRecords);
        System.out.println();
        System.out.println("INFO: " + infoCount);
        System.out.println("WARN: " + warnCount);
        System.out.println("ERROR: " + errorCount);

        sortServicesByErrorRateDescending(serviceStats, serviceCount);

        System.out.println();
        System.out.println("SERVICE STATISTICS");
        for (int i = 0; i < serviceCount; i++) {
            ServiceStats s = serviceStats[i];
            System.out.println(s.getServiceName() + " total=" + s.getTotal()
                    + " errors=" + s.getErrorCount()
                    + " errorRate=" + s.getErrorRatePercentString());
        }

        System.out.println();
        System.out.println("INCIDENTS");
        if (incidentCount == 0) {
            System.out.println("No incidents detected.");
        } else {
            for (int i = 0; i < incidentCount; i++) {
                Incident inc = incidents[i];
                System.out.println("Service: " + inc.getServiceName());
                System.out.println("First Error: " + inc.getFirstErrorTimestamp());
                System.out.println("Last Error: " + inc.getLastErrorTimestamp());
                System.out.println();
            }
        }
    }

    // Doubles capacity and manually copies existing LogEntry elements over
    private static LogEntry[] growEntryArray(LogEntry[] array) {
        LogEntry[] newArray = new LogEntry[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Doubles capacity and manually copies existing ServiceStats elements over
    private static ServiceStats[] growServiceArray(ServiceStats[] array) {
        ServiceStats[] newArray = new ServiceStats[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Doubles capacity and manually copies existing Incident elements over
    private static Incident[] growIncidentArray(Incident[] array) {
        Incident[] newArray = new Incident[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Doubles capacity and manually copies existing String elements over
    private static String[] growStringArray(String[] array) {
        String[] newArray = new String[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Doubles capacity and manually copies existing int elements over
    private static int[] growIntArray(int[] array) {
        int[] newArray = new int[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            newArray[i] = array[i];
        }
        return newArray;
    }

    // Manual linear search for an existing service; -1 if not found yet
    private static int findServiceIndex(ServiceStats[] array, int count, String serviceName) {
        for (int i = 0; i < count; i++) {
            if (array[i].matchesService(serviceName)) {
                return i;
            }
        }
        return -1;
    }

    // Manual linear search over the Q6 incident-tracking service names
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
                    // more delimiters than a 5-field line should have
                    return null;
                }
                fields[fieldIndex] = line.substring(start, i);
                fieldIndex++;
                start = i + 1;
            }
        }

        if (fieldIndex != field_count - 1) {
            // too few delimiters found
            return null;
        }

        fields[fieldIndex] = line.substring(start);
        return fields;
    }

    // Q2: checks level, request ID, and timestamp validity
    private static boolean isValidRecord(String[] fields) {
        String timestamp = fields[timestamp_field_index];
        String level = fields[level_field_index];
        String requestId = fields[request_id_field_index];

        return isValidTimestamp(timestamp) && isValidLevel(level) && isValidRequestId(requestId);
    }

    private static boolean isValidLevel(String level) {
        return level.equals("INFO") || level.equals("WARN") || level.equals("ERROR");
    }

    // request ID must be all digits and represent a positive integer
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

    // must be exactly 19 chars: YYYY-MM-DD HH:MM:SS, with month 1-12
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

    // Q5: sorts services by error rate descending, ties broken by
    // ascending alphabetical order, using radix sort as required.
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

    // Selection sort by service name ascending. Not bubble sort, not a
    // library sort - allowed under the assignment's restrictions.
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

    // One stable counting-sort pass over a single decimal digit, per
    // the LSD radix sort algorithm. Bucket array is allocated at size
    // 12 as required; only indices 0-9 are ever used.
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
