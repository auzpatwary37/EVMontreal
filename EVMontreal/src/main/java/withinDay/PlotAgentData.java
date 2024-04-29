package withinDay;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class PlotAgentData {
    public static void main(String[] args) {
        String inputSheetName = "InputSheet";
        String baseSheetName = "BaseScenarioSheet";
        String pricedSheetName = "PricedScenarioSheet";
        String chartSheetName = "ChartSheet";
        String agentID;

        try (FileInputStream file = new FileInputStream("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\Final.xlsx")) {
            Workbook workbook = new XSSFWorkbook(file);
            Sheet inputSheet = workbook.getSheet(inputSheetName);
            Sheet baseSheet = workbook.getSheet(baseSheetName);
            Sheet pricedSheet = workbook.getSheet(pricedSheetName);
            Sheet chartSheet = workbook.getSheet(chartSheetName);

            // Get agent ID from the input sheet
           String agentID1 = inputSheet.getRow(0).getCell(0).getStringCellValue();

            // Find and plot agent data in base scenario
            plotAgentData(baseSheet, chartSheet, agentID1);

            // Find and plot agent data in priced scenario
            plotAgentData(pricedSheet, chartSheet, agentID1);

            // Save changes to Excel file
            try (FileOutputStream outFile = new FileOutputStream("your_output_file.xlsx")) {
                workbook.write(outFile);
                System.out.println("Agent data plotted successfully.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void plotAgentData(Sheet dataSheet, Sheet chartSheet, String agentID) {
        int timeColumnIndex = 1; // Assuming time is in column B
        int socColumnIndex = 2;  // Assuming SOC is in column C

        for (Row row : dataSheet) {
            Cell agentIDCell = row.getCell(0);
            if (agentIDCell != null && agentIDCell.getStringCellValue().equals(agentID)) {
                Cell timeCell = row.getCell(timeColumnIndex);
                Cell socCell = row.getCell(socColumnIndex);
                
                // Create a new row in the chart sheet
                Row chartRow = chartSheet.createRow(chartSheet.getLastRowNum() + 1);
                // Copy time and SOC values to the new row
                chartRow.createCell(0).setCellValue(timeCell.getNumericCellValue());
                chartRow.createCell(1).setCellValue(socCell.getNumericCellValue());
            }
        }
    }
}


