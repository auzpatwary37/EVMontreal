package binding;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;

public class ChargingStartEventUrbanEv extends ChargingStartEvent{
	private Person person;
	public ChargingStartEventUrbanEv(double time, Id<Charger> chargerId, Id<ElectricVehicle> vehicleId,
			String chargerType, Person person) {
		super(time, chargerId, vehicleId, chargerType);
		this.person = person;
		// TODO Auto-generated constructor stub
	}
	public Person getPerson() {
		return this.person;
	}

}
