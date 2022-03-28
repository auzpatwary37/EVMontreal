package EVPricing;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

public class readAndFixPopulation {
public static void main(String[] args) {
	Config config  = ConfigUtils.createConfig();
	//ConfigUtils.loadConfig(config,"C:\\Users\\arsha\\git\\EVMontreal-2\\EVMontreal\\Montreal_Scenario_New\\config.xml");//give location for config file
	config.facilities().setInputFile("C:\\Users\\arsha\\git\\EVMontreal-2\\EVMontreal\\Montreal_Scenario_New\\Siouxfalls_facilities.xml.gz");// give the facility file location
	config.plans().setInputFile("C:\\Users\\arsha\\git\\EVMontreal-2\\EVMontreal\\Montreal_Scenario_New\\Siouxfalls_population.xml");//give the population location here
	config.network().setInputFile("C:\\Users\\arsha\\git\\EVMontreal-2\\EVMontreal\\Montreal_Scenario_New\\Siouxfalls_network_PT.xml");
	Scenario scenario = ScenarioUtils.loadScenario(config);

	
	Population pop = scenario.getPopulation();
	
	ActivityFacilities fac = scenario.getActivityFacilities();
	Network net = scenario.getNetwork();
	for(Person p : pop.getPersons().values()) {
		for(PlanElement pe:p.getSelectedPlan().getPlanElements()) {
			if(pe instanceof Activity) {
				Activity a = ((Activity)pe);
				ActivityFacility f= fac.getFacilities().get(a.getFacilityId());
				Id<Link> lId = f.getLinkId();
				if(f.getLinkId()==null) {
					lId = NetworkUtils.getNearestLink(net, a.getCoord()).getId();
				}
				a.setLinkId(lId);
				//System.out.println();
			}
		}
	}
	new PopulationWriter(pop).write("NewPopulation.xml");// give the write location of the new population 
}
}



