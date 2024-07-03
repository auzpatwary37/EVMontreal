package withinDay;

//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//
//public class readExcel {
//	 public static void main(String[] args) {
//	        String inputFile = "C:\\\\\\\\Users\\\\\\\\arsha\\\\\\\\OneDrive\\\\\\\\Desktop\\\\\\\\final\\\\\\\\39.chargingStats.xlsx";
//
//	        try {
//	            FileInputStream fis = new FileInputStream(inputFile);
//	            Workbook workbook = new XSSFWorkbook(fis);
//
//	            Sheet sheet = workbook.getSheetAt(0); // Assuming the first sheet
//
//	            List<Double> iValues = new ArrayList<>();
//	            List<Double> jValues = new ArrayList<>();
//	            List<Double> hValues = new ArrayList<>();
//
//	            // Read values from input sheet
//	            for (Row row : sheet) {
//	            	Cell iCell = row.getCell(0);
//	            	Cell jCell = row.getCell(1);
//	            	Cell hCell = row.getCell(2);
//
//	                double i = iCell.getNumericCellValue();
//	                double j = jCell.getNumericCellValue();
//	                double h = hCell.getNumericCellValue();
//
//	                iValues.add(i);
//	                jValues.add(j);
//	                hValues.add(h);
//	            }
//
//	            Sheet outputSheet = workbook.createSheet("analyse77.xlsx"); // Create a new sheet for the results
//
//	            int rowIndex = 0;
//	            // Perform column operations
//	            for (int index = 0; index < iValues.size(); index++) {
//	                double i = iValues.get(index);
//	                double j = jValues.get(index);
//	                double k = j - i;
//
//	                int numNewRows = (int) Math.abs(k);
//	                double h = hValues.get(index);
//	                double hPerRow = h / (numNewRows + 1);
//
//	                Row row = outputSheet.createRow(rowIndex++);
//	                row.createCell(0).setCellValue(i);
//	                row.createCell(1).setCellValue(j);
//	                row.createCell(2).setCellValue(k);
//	                row.createCell(3).setCellValue(h);
//
//	                for (int newRow = 0; newRow < numNewRows; newRow++) {
//	                    row = outputSheet.createRow(rowIndex++);
//	                    row.createCell(0).setCellValue(i + newRow + 1);
//	                    row.createCell(3).setCellValue(hPerRow);
//	                }
//	            }
//
//	            // Write the updated workbook to the same file
//	            FileOutputStream fos = new FileOutputStream(inputFile);
//	            workbook.write(fos);
//	            fos.close();
//
//	            workbook.close();
//
//	            System.out.println("Column operations completed and results written to the output sheet in the same Excel file.");
//	        } catch (IOException e) {
//	            e.printStackTrace();
//	        }
//	    }
//}


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class readExcel {
    public static void main(String[] args) {
        String inputFile = "C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\ResultsDrEbrahimi\\\\Flat\\\\159.chargingStatsFlat.xlsx";

        try {
            FileInputStream fis = new FileInputStream(inputFile);
            Workbook workbook = new XSSFWorkbook(fis);

            Sheet sheet = workbook.getSheetAt(1); // Assuming the first sheet

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row currentRow = sheet.getRow(rowIndex);
                Row aboveRow = sheet.getRow(rowIndex - 1);

                Cell currentCellB = currentRow.getCell(1);
                Cell currentCellD = currentRow.getCell(2);

                Cell aboveCellD = aboveRow.getCell(3);

                if (currentCellB == null && currentCellD != null) {
                    aboveCellD.setCellValue(currentCellD.getNumericCellValue());
                }
            }

            // Write the updated workbook to the same file
            FileOutputStream fos = new FileOutputStream(inputFile);
            workbook.write(fos);
            fos.close();

            workbook.close();

            System.out.println("Column operations completed. Values in column D updated as required.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


