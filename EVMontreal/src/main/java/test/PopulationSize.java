package test;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicles;

	public class PopulationSize {
	    public static void main(String[] args) {
	        String configFile = "C:\\Users\\arsha\\git\\EVMontreal_newVersion\\EVMontreal\\TRB2024\\config_with_calibrated_parameters.xml"; // Replace with the path to your MATSim configuration file

	        // Load the MATSim configuration and scenario
	        Config config = ConfigUtils.loadConfig(configFile);
	        config.plans().setInputFile("C:\\Users\\arsha\\git\\EVMontreal_newVersion\\EVMontreal\\TRB2024\\population_with_locations10P.xml.gz");
	        config.vehicles().setVehiclesFile("C:\\Users\\arsha\\git\\EVMontreal_newVersion\\EVMontreal\\TRB2024\\vehicle.xml");
	        Scenario scenario = ScenarioUtils.loadScenario(config);

	        // Get the population from the scenario
	        Population population = scenario.getPopulation();
	        Vehicles v =scenario.getVehicles();
	        
	        // Get the population size (number of agents)
	        int populationSize = population.getPersons().size();

	        System.out.println("Population size in the MATSim scenario: " + populationSize);
	        System.out.println("Total number of vehicles = "+ v.getVehicles().size());
	    }
	}

