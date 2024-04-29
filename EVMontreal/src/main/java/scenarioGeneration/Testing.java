package scenarioGeneration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.ev.fleet.ElectricFleetReader;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerReader;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.MatsimFacilitiesReader;


public class Testing {
public static void main(String[] args) {
	ChargingInfrastructureSpecification chargingInfrastructureSpecification = new ChargingInfrastructureSpecificationImpl();
	new ChargerReader(chargingInfrastructureSpecification).parse(
			IOUtils.getFileUrl("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\charger.xml"));
	
	ElectricFleetSpecification fleetSpecification = new ElectricFleetSpecificationImpl();
	new ElectricFleetReader(fleetSpecification).parse(
			IOUtils.getFileUrl("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml"));
	
	Population pop = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\plan.xml");
	
	Map<Id<Charger>,Set<Id<Person>>> personsToChargers = new HashMap<>();
	Map<Id<Link>,Id<Charger>> chargers = new HashMap<>();
	Scenario scn = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	new MatsimFacilitiesReader(scn).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\montreal_facilities.xml.gz");
	ActivityFacilities fac = scn.getActivityFacilities();
	int home = 0;
	for(ChargerSpecification c:chargingInfrastructureSpecification.getChargerSpecifications().values()){
		personsToChargers.put(c.getId(), new HashSet<>());
		chargers.put(c.getLinkId(), c.getId());
		if(c.getId().toString().contains("home"))home++;
	}
	
	for(Person p:pop.getPersons().values()){
		if(fleetSpecification.getVehicleSpecifications().containsKey(Id.create(p.getId().toString(), ElectricVehicle.class))) {
			TripStructureUtils.getActivities(p.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).forEach(a->{
				if(chargers.containsKey(fac.getFacilities().get(a.getFacilityId()).getLinkId())) {
					personsToChargers.get(chargers.get(fac.getFacilities().get(a.getFacilityId()).getLinkId())).add(p.getId());
				}
			});
		}
	}
	
	int zeroPerson = 0;
	
	for(Entry<Id<Charger>, Set<Id<Person>>> e:personsToChargers.entrySet()) {
		if(e.getValue().size()==0)zeroPerson++;
	}
	System.out.println(zeroPerson);
	System.out.println(home);
}
}
