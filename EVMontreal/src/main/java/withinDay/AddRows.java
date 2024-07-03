package withinDay;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class AddRows {
    public static void main(String[] args) {
        try (FileInputStream fileInputStream = new FileInputStream("C:\\\\Users\\\\arsha\\\\OneDrive\\\\Desktop\\\\ResultsDrEbrahimi\\\\Flat\\\\159.chargingStatsFlat.xlsx");
             Workbook workbook = new XSSFWorkbook(fileInputStream);
             FileOutputStream fileOutputStream = new FileOutputStream("C:\\\\\\\\Users\\\\\\\\arsha\\\\\\\\OneDrive\\\\\\\\Desktop\\\\\\\\ResultsDrEbrahimi\\\\\\\\Flat\\\\\\\\159.chargingStatsFlatOutput.xlsx")) {

            // Assuming the Excel file has three columns in the first sheet
            Sheet sheet = workbook.getSheetAt(1);
            int lastRow = sheet.getLastRowNum();

         // ...

         for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
             Row row = sheet.getRow(rowIndex);
             Cell cell3 = row.getCell(2); // Third Column

             if (cell3 != null && cell3.getCellType() == Cell.CELL_TYPE_NUMERIC) {
                 double value3 = cell3.getNumericCellValue();
                 int rowCountToAdd = (int) value3;

                 // Create empty rows based on the value in the third column
                 for (int i = 0; i < rowCountToAdd; i++) {
                     sheet.shiftRows(rowIndex + 1, lastRow + 1, 1);
                     Row emptyRow = sheet.createRow(rowIndex + 1);
                     emptyRow.createCell(0, Cell.CELL_TYPE_BLANK);
                     emptyRow.createCell(1, Cell.CELL_TYPE_BLANK);
                     emptyRow.createCell(2, Cell.CELL_TYPE_BLANK);
                 }

                 // Increment rowIndex by the number of empty rows added
                 rowIndex += rowCountToAdd;
                 lastRow += rowCountToAdd;
             }
         }


            // Write the modified workbook to a new file
            workbook.write(fileOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

