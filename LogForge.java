import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;



public class LogForge {
    private static final int field_count = 5;
    private static final int level_field_index = 2;
    
    public static void main(String[] args) {
        if(args.length < 1){
            System.out.println("Usage: java LogForge <logfile>");
            return;
        }
        String filename = args[0];
        int totalRecords = 0;
        int infoCount = 0;
        int warnCount = 0;
        int errorCount = 0;
        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new File(filename));   
            
            while (fileScanner.hasNextLine()){
                String line = fileScanner.nextLine();
                String[] fields = splitFields(line);

                if(fields == null){
                    continue;
                }
                totalRecords++;
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
        
        System.out.println("Total records: " + totalRecords);
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
}