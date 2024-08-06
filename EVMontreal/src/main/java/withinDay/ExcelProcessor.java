package withinDay;

//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.*;
//
//public class ExcelProcessor {
//    public static void main(String[] args) throws IOException {
//        // Specify the column indices for i and j (starting from 0)
//        int iColumnIndex = 8; // Column index of column i
//        int jColumnIndex = 9; // Column index of column j
//
//        // Load the Excel file
//        FileInputStream inputStream = new FileInputStream("C:\\Users\\arsha\\OneDrive\\Desktop\\result.xlsx");
//        Workbook workbook = new XSSFWorkbook(inputStream);
//        Sheet sheet = workbook.getSheetAt(0);
//
//        // Find the last row index
//        int lastRow = sheet.getLastRowNum();
//
//        // Iterate through the rows and perform the operations
//        for (int row = 1; row <= lastRow; row++) { // Start from row 1 assuming row 0 is the header row
//            Row currentRow = sheet.getRow(row);
//
//            // Read the values of i and j from the current row
//            Cell iCell = currentRow.getCell(iColumnIndex);
//            Cell jCell = currentRow.getCell(jColumnIndex);
//            if (iCell !=null && jCell != null) {
//            int i = (int) iCell.getNumericCellValue();
//            int j = (int) jCell.getNumericCellValue();
//            // Calculate the difference (i - j)
//            int diff = i - j;
//
//            // Shift rows down to create empty rows based on the difference
//            sheet.shiftRows(row + 1, lastRow + 1, diff);
//
//
//            // Add values of column i with 1, 2, ..., (i - j) in the newly inserted rows
//            for (int k = 1; k <= diff; k++) {
//                Row newRow = sheet.createRow(row + k);
//                Cell newCell = newRow.createCell(iColumnIndex);
//                newCell.setCellValue(i + k);
//            }
//
//            // Update the last row index
//            lastRow += diff;
//        }
//        }
//
//        // Save the modified Excel file
//        FileOutputStream outputStream = new FileOutputStream("C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\output.xlsx");
//        workbook.write(outputStream);
//        workbook.close();
//        outputStream.close();
//        System.out.println("Excel file processed successfully.");
//    }
//}


//*************************************************************************************
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.*;
//
//public class ExcelProcessor {
//    public static void main(String[] args) throws IOException {
//        // Specify the column indices for i and j (starting from 0)
//        int iColumnIndex = 1; // Column index of column i
//        int jColumnIndex = 2; // Column index of column j
//
//        // Load the Excel file
//        FileInputStream inputStream = new FileInputStream("C:\\Users\\arsha\\OneDrive\\Desktop\\result.xlsx");
//        Workbook workbook = new XSSFWorkbook(inputStream);
//        Sheet mainSheet = workbook.getSheetAt(0);
//
//        // Create a new sheet for the results
//        Sheet resultSheet = workbook.createSheet("Results");
//
//        // Find the last row index in the main sheet
//        int lastRow = mainSheet.getLastRowNum();
//
//        // Iterate through the rows and perform the operations
//        int resultRowIndex = 0;
//        for (int row = 1; row <= lastRow; row++) { // Start from row 1 assuming row 0 is the header row
//            Row currentRow = mainSheet.getRow(row);
//
////            // Read the values of i and j from the current row
////            Cell iCell = currentRow.getCell(iColumnIndex);
////            Cell jCell = currentRow.getCell(jColumnIndex);
////            int i = (int) iCell.getNumericCellValue();
////            int j = (int) jCell.getNumericCellValue();
//            
//         // Read the values of i and j from the current row
//            Cell iCell = currentRow.getCell(iColumnIndex);
//            Cell jCell = currentRow.getCell(jColumnIndex);
//
//            int i = (int) iCell.getNumericCellValue();
//            int j = (jCell != null) ? (int) jCell.getNumericCellValue() : 0;
//
//
//            // Calculate the difference (i - j)
//            int diff = i - j;
//
//            // Add the values of column i with 1, 2, ..., (i - j) in the result sheet
//            for (int k = 1; k <= diff; k++) {
//                Row resultRow = resultSheet.createRow(resultRowIndex++);
//                Cell resultCell = resultRow.createCell(iColumnIndex);
//                resultCell.setCellValue(i + k);
//            }
//        }
//
//        // Save the modified Excel file
//        FileOutputStream outputStream = new FileOutputStream("C:\\Users\\arsha\\OneDrive\\Desktop\\output.xlsx");
//        workbook.write(outputStream);
//        workbook.close();
//        outputStream.close();
//        System.out.println("Excel file processed successfully.");
//    }
//}

//****************************************************************************************************

//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.*;
//
//public class ExcelProcessor {
//    public static void main(String[] args) throws IOException {
//        // Specify the column indices for i, j, and k (starting from 0)
//        int iColumnIndex = 0; // Column index of column i
//        int jColumnIndex = 1; // Column index of column j
//        int kColumnIndex = 2; // Column index of column k
//
//        // Load the Excel file
//        FileInputStream inputStream = new FileInputStream("C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\result.xlsx");
//        Workbook workbook = new XSSFWorkbook(inputStream);
//        Sheet sheet = workbook.getSheetAt(0);
//
//        // Find the last row index
//        int lastRow = sheet.getLastRowNum();
//
//        // Iterate through the rows and perform the operations
//        for (int row = 1; row <= lastRow; row++) { // Start from row 1 assuming row 0 is the header row
//            Row currentRow = sheet.getRow(row);
//
//            // Read the values of i and j from the current row
//            Cell iCell = currentRow.getCell(iColumnIndex);
//            Cell jCell = currentRow.getCell(jColumnIndex);
//            int i = (int) iCell.getNumericCellValue();
//            int j = (int) jCell.getNumericCellValue();
//
//            // Calculate the difference (i - j) and add it to column k
//            int diff = j - i;
//            Cell kCell = currentRow.createCell(kColumnIndex);
//            kCell.setCellValue(diff);
//
//            // Insert empty rows based on the value in column k
//            if (diff > 0) {
//                sheet.shiftRows(row + 1, lastRow + 1, diff);
//                for (int k = 1; k <= diff; k++) {
//                    Row emptyRow = sheet.createRow(row + k);
//                }
//                row += diff;
//                lastRow += diff;
//            }
//        }
//
//        // Save the modified Excel file
//        FileOutputStream outputStream = new FileOutputStream("C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\output.xlsx");
//        workbook.write(outputStream);
//        workbook.close();
//        outputStream.close();
//        System.out.println("Excel file processed successfully.");
//    }
//}



import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ExcelProcessor {
    public static void main(String[] args) throws IOException {
        // Specify the column indices for i, j, and k (starting from 0)
        int iColumnIndex = 0; // Column index of column i
        int jColumnIndex = 1; // Column index of column j
        int kColumnIndex = 2; // Column index of column k

        // Load the Excel file
        FileInputStream inputStream = new FileInputStream("C:\\Users\\arsha\\OneDrive\\Desktop\\ResultsDrEbrahimi\\Flat\\159.chargingStatsFlat.xlsx");
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(1);

        // Find the last row index
        int lastRow = sheet.getLastRowNum();

        // Iterate through the rows and perform the operations
        for (int row = 1; row <= lastRow; row++) { // Start from row 1 assuming row 0 is the header row
            Row currentRow = sheet.getRow(row);

            // Read the values of i and j from the current row
            Cell iCell = currentRow.getCell(iColumnIndex);
            Cell jCell = currentRow.getCell(jColumnIndex);
            int i = (int) iCell.getNumericCellValue();
            int j = (int) jCell.getNumericCellValue();

            // Calculate the difference (j - i)
            int diff = j - i;

            // Insert empty rows based on the difference
            if (diff > 0) {
                sheet.shiftRows(row + 1, lastRow + 1, diff);
                for (int k = 1; k <= diff; k++) {
                    Row emptyRow = sheet.createRow(row + k);
                    Cell iEmptyCell = emptyRow.createCell(iColumnIndex);
                    iEmptyCell.setCellValue(i + k);
                }
                row += diff;
                lastRow += diff;
            }
        }

        // Save the modified Excel file
        FileOutputStream outputStream = new FileOutputStream("C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\ResultsDrEbrahimi\\\\Flat\\\\159.chargingStatsFlatOutput.xlsx");
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
        System.out.println("Excel file processed successfully.");
    }
}


