package test;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

public class MatsimVehicleCounter {
    public static void main(String[] args) {
        String configFile = "C:\\Users\\arsha\\OneDrive\\Desktop\\ABMTrans\\config_with_calibrated_parameters.xml"; // Replace with the path to your MATSim configuration file

        // Load the MATSim configuration and scenario
        Config config = ConfigUtils.loadConfig(configFile);
        org.matsim.api.core.v01.Scenario scenario = ScenarioUtils.loadScenario(config);

        // Get the population and vehicles from the scenario
        Population population = scenario.getPopulation();
        Vehicles vehicles = scenario.getVehicles();

        // Count the number of vehicles
        int numberOfVehicles = countVehicles(vehicles);

        System.out.println("Number of vehicles in the MATSim scenario: " + numberOfVehicles);
    }

    private static int countVehicles(Vehicles vehicles) {
        int count = 0;

        for (Vehicle vehicle : vehicles.getVehicles().values()) {
            count++;
        }

        return count;
    }
}
