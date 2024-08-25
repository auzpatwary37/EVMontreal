package withinDay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class TimeInterval {
    public static void main(String[] args) {

        String inputFile = "C:\\Users\\arsha\\Desktop\\1P\\Priced.xlsx";
        String outputFile = "C:\\Users\\arsha\\Desktop\\1P\\PricedOutput.xlsx";


        try {
            FileInputStream fileInputStream = new FileInputStream(new File(inputFile));
            Workbook workbook = new XSSFWorkbook(fileInputStream);
            Sheet sheet = workbook.getSheetAt(1);

            List<Double> headers = getHeaders(sheet);

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Cell firstColumn = row.getCell(0);
                Cell secondColumn = row.getCell(1);

                double startTime = firstColumn.getNumericCellValue();
                double endTime = secondColumn.getNumericCellValue();

                int columnIndex = 2;
                for (double header : headers) {
                    Cell cell = row.createCell(columnIndex++);
                    if (header >= startTime && header <= endTime) {
                        cell.setCellValue(1);
                    } else {
                        cell.setCellValue(0);
                    }
                }
            }

            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            workbook.write(fileOutputStream);
            workbook.close();
            fileInputStream.close();
            fileOutputStream.close();

            System.out.println("Output file created successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<Double> getHeaders(Sheet sheet) {
        List<Double> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        int lastColumnIndex = headerRow.getLastCellNum() - 1;

        for (int columnIndex = 2; columnIndex <= lastColumnIndex; columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            double headerValue = headerCell.getNumericCellValue();
            headers.add(headerValue);
        }

        return headers;
    }
}
