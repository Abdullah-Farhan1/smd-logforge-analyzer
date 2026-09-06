import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;



public class LogForge {
    private static final int field_count = 5;
    private static final int timestamp_field_index = 0;
    private static final int level_field_index = 2;
    private static final int request_id_field_index = 3;
    private static final int timestamp_length = 19;
    
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
                String level = fields[level_field_index];
                if(level.equals("INFO")) infoCount++;
                else if(level.equals("WARN")) warnCount++;
                else if(level.equals("ERROR")) errorCount++;
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
        
        System.out.println("Total lines: " + totalLines);
        System.out.println("Valid records: " + validRecords);
        System.out.println("Invalid records: " + invalidRecords);
        System.out.println();
        System.out.println("INFO: " + infoCount);
        System.out.println("WARN: " + warnCount);
        System.out.println("ERROR: " + errorCount);
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
}