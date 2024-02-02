package scenarioGeneration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.ev.fleet.ElectricFleetReader;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerReader;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.utils.io.IOUtils;


public class Testing {
public static void main(String[] args) {
	ChargingInfrastructureSpecification chargingInfrastructureSpecification = new ChargingInfrastructureSpecificationImpl();
	new ChargerReader(chargingInfrastructureSpecification).parse(
			IOUtils.getFileUrl(""));
	
	ElectricFleetSpecification fleetSpecification = new ElectricFleetSpecificationImpl();
	new ElectricFleetReader(fleetSpecification).parse(
			IOUtils.getFileUrl(""));
	
	Population pop = PopulationUtils.readPopulation("");
	
	Map<Id<Charger>,Set<Id<Person>>> personsToChargers = new HashMap<>();
	Map<Id<Link>,Id<Charger>> chargers = new HashMap<>();
	
	
	chargingInfrastructureSpecification.getChargerSpecifications().values().forEach(c->{
		personsToChargers.put(c.getId(), new HashSet<>());
		chargers.put(c.getLinkId(), c.getId());
	});
	
	pop.getPersons().values().forEach(p->{
		if(fleetSpecification.getVehicleSpecifications().containsKey(Id.create(p.getId().toString(), ElectricVehicle.class))) {
			TripStructureUtils.getActivities(p.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).forEach(a->{
				if(personsToChargers.containsKey(a.getLinkId())) {
					personsToChargers.get(chargers.get(a.getLinkId())).add(p.getId());
				}
			});
		}
	});
	
	int zeroPerson = 0;
	
	for(Entry<Id<Charger>, Set<Id<Person>>> e:personsToChargers.entrySet()) {
		if(e.getValue().size()==0)zeroPerson++;
	}
	System.out.println(zeroPerson);
}
}
