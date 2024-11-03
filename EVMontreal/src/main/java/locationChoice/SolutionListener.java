package locationChoice;


import org.opt4j.core.optimizer.Archive;
import org.opt4j.core.optimizer.OptimizerIterationListener;
import org.joda.time.LocalDate;
import org.matsim.api.core.v01.Id;
import org.opt4j.core.Individual;
import org.opt4j.core.Objective;
import org.opt4j.core.Value;

import javax.inject.Inject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

public class SolutionListener implements OptimizerIterationListener{

    @Inject
    private Archive archive;
    private String logFilePath = "data/10p/solutionsAtIteration_";
    private int logInterval= 50;

    // Constructor to initialize with Archive, log file path, and logging interval
    @Inject
    public SolutionListener() {

    }

    // Method called whenever the archive updates

    // Method called at the end of each iteration
    @Override
    public void iterationComplete(int iteration) {

        
        // Write solutions to file at the specified interval
        if (iteration % logInterval == 0) {
            System.out.println("Writing solutions to file at iteration " + iteration);
            writeSolutionsToFile(this.archive,this.logFilePath+iteration+"_"+LocalDate.now().toString()+"_.csv");
        }
    }

 // Method to write optimization details to a file
    public void writeSolutionsToFile(Archive archive, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) { // Append mode

            // Iterate over each individual in the archive
            for (Individual individual : archive) {
                // Write header for a new solution
                writer.write("Hotspot ID, Charger Type, Plug Count\n");

                // Get the phenotype (solution representation)
                Map<Id<Hotspot>, Map<ChargerType, Integer>> phenotypeMap = (Map<Id<Hotspot>, Map<ChargerType, Integer>>) individual.getPhenotype();

                // Write each Hotspot ID, Charger Type, and Plug Count as a row
                for (Map.Entry<Id<Hotspot>, Map<ChargerType, Integer>> hotspotEntry : phenotypeMap.entrySet()) {
                    Id<Hotspot> hotspotId = hotspotEntry.getKey();
                    Map<ChargerType, Integer> chargerMap = hotspotEntry.getValue();

                    // Write each charger type and plug count for this hotspot
                    for (Map.Entry<ChargerType, Integer> chargerEntry : chargerMap.entrySet()) {
                        ChargerType chargerType = chargerEntry.getKey();
                        int plugCount = chargerEntry.getValue();
                        writer.write(hotspotId + ", " + chargerType + ", " + plugCount + "\n");
                    }
                }

                // Add a blank line for separation before objectives
                writer.write("\nObjectives\n");

                // Write each objective as a key-value pair
                for (Entry<Objective, Value<?>> objective : individual.getObjectives()) {
                    writer.write(objective.getKey() + ", " + objective.getValue() + "\n");
                }

                // Add a blank line between solutions
                writer.write("\n\n");
            }

            System.out.println("Solutions written to file: " + filePath);

        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }
}

