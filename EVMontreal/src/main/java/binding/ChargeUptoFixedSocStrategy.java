package binding;

import org.matsim.contrib.ev.charging.ChargeUpToMaxSocStrategy;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;

public class ChargeUptoFixedSocStrategy extends ChargeUpToMaxSocStrategy {
	
	public final double fixedSOC;

	public ChargeUptoFixedSocStrategy(ChargerSpecification charger, double maxRelativeSoc, double fixedSOC) {
		super(charger, maxRelativeSoc);
		this.fixedSOC = fixedSOC;
	}
	@Override
	public boolean isChargingCompleted(ElectricVehicle ev) {
		return ev.getBattery().getSoc()/ev.getBattery().getCapacity()>=this.fixedSOC;
	}

}
