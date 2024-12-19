package testing;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;

public class Test1 {
public static void main(String[] args) {
	Population pop = PopulationUtils.readPopulation("G:\\ai\\aiagent_1124_3\\output_plans.xml.gz");
	int haveBoth = 0;
	int selected = 0;
	
	int logicBased = 0;
	int selectedLogicBased = 0;
	for(Person p:pop.getPersons().values()) {
		for(Plan plan:p.getPlans()) {
			if(plan.getAttributes().getAttribute("IfAiGenerated")!=null) {
				haveBoth++;
				if(plan.equals(plan.getPerson().getSelectedPlan()))selected++;
			}
		}
	}
	
	for(Person p:pop.getPersons().values()) {
		for(Plan plan:p.getPlans()) {
			if(plan.getAttributes().getAttribute("logicSwitch")!=null) {
				logicBased++;
				if(plan.equals(plan.getPerson().getSelectedPlan()))selectedLogicBased++;
			}
		}
	}
	System.out.println(haveBoth);
	System.out.println(selected);
	System.out.println(logicBased);
	System.out.println(selectedLogicBased);
	
	 String inputFilePath = "G:\\ai\\aiagent_1124_3\\logfile.log";
     String outputFilePath = "G:\\ai\\aiagent_1124_3\\logfile_filtered.log";
     String outputFilePath2 = "G:\\ai\\aiagent_1124_3\\logfile_filtered2.csv";
     
     filterLogFile(inputFilePath, outputFilePath);
     extractReplanningNumbers(inputFilePath,outputFilePath2);
	
}

public static void filterLogFile(String inputFilePath, String outputFilePath) {
    // Keywords to search for in each line
    String[] keywords = {"ITERATION", "AIAgentReplanningModule:312", "Total unsuccessful replanning", "UrbanEVTripPlanningStrategyModule:1485"};
    
    try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
         BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

        String line;
        while ((line = reader.readLine()) != null) {
            // Check if the line contains any of the keywords
            for (String keyword : keywords) {
                if (line.contains(keyword)) {
                    writer.write(line);
                    writer.newLine();
                    break; // Move to the next line once a keyword is matched
                }
            }
        }
        
        System.out.println("Filtered log written to " + outputFilePath);
    } catch (IOException e) {
        System.err.println("Error processing the log file: " + e.getMessage());
    }
}

//Function to extract "replanning<d>plans" patterns and write only the extracted number d to a CSV file
public static void extractReplanningNumbers(String inputFilePath, String outputFilePath) {
    // Pattern to match "replanning" followed by a number and "plans"
    Pattern pattern = Pattern.compile("replanning(\\d+)plans");

    try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
         BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {

        writer.write("ReplanningCount"); // Header for the CSV column
        writer.newLine();

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String replanningCount = matcher.group(1); // Extract the number d
                writer.write(replanningCount);
                writer.newLine();
            }
        }
        
        System.out.println("Replanning counts written to " + outputFilePath);
    } catch (IOException e) {
        System.err.println("Error processing the log file: " + e.getMessage());
    }
}

}
