package binding;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.charging.ChargingEndEvent;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.vehicles.Vehicle;

public class ChargingEndEventUrbanEv extends ChargingEndEvent{
	
	private Person person;

	public ChargingEndEventUrbanEv(double time, Id<Charger> chargerId, Id<Vehicle> vehicleId, double charge, Person person) {
		super(time, chargerId, vehicleId,charge);
		// TODO Auto-generated constructor stub
		this.person = person;
	}

	public Person getPerson() {
		return this.person;
	}
}
