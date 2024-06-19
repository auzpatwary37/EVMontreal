package binding;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.contrib.ev.fleet.ElectricFleets;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecificationImpl;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * 
 * @author Ashraf
 *
 */
public class ElectricFleetModule extends AbstractModule {
	@Inject
	private EvConfigGroup evCfg;
	
	@Inject
	private Scenario scenario;

	@Override
	public void install() {
		bind(ElectricFleetSpecification.class).toProvider(() -> {
			ElectricFleetSpecification fleetSpecification = new ElectricFleetSpecificationImpl();
			ElectricVehicleSpecificationImpl.createAndAddVehicleSpecificationsFromMatsimVehicles(fleetSpecification, scenario.getVehicles().getVehicles().values());
			return fleetSpecification;
		}).asEagerSingleton();
		installQSimModule(new AbstractQSimModule() {
		@Override
		protected void configureQSim() {
			bind(ElectricFleet.class).toProvider(new Provider<>() {
				@Inject
				private ElectricFleetSpecification fleetSpecification;
				@Inject
				private DriveEnergyConsumption.Factory driveConsumptionFactory;
				@Inject
				private AuxEnergyConsumption.Factory auxConsumptionFactory;
				@Inject
				private ChargingPower.Factory chargingPowerFactory;

				@Override
				public ElectricFleet get() {
					return ElectricFleets.createDefaultFleet(fleetSpecification, driveConsumptionFactory,
							auxConsumptionFactory, chargingPowerFactory);
				}
			}).asEagerSingleton();
		}
	});
	}
		
		
}