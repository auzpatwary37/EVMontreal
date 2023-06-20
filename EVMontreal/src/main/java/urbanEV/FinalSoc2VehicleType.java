package urbanEV;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.ev.EvUnits;
import org.matsim.contrib.ev.fleet.*;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.mobsim.framework.events.MobsimBeforeCleanupEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeCleanupListener;
import org.matsim.core.mobsim.framework.listeners.MobsimListener;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

import javax.inject.Provider;


class FinalSoc2VehicleType implements MobsimBeforeCleanupListener {
    ElectricFleet electricFleet;
    Vehicles vehicles;
    ElectricFleetSpecification spec;


    FinalSoc2VehicleType(ElectricFleet electricFleet, Vehicles vehicles, ElectricFleetSpecification spec){
        this.electricFleet = electricFleet;
        this.vehicles = vehicles;
        this.spec = spec;
    }

    @Override
    public void notifyMobsimBeforeCleanup(MobsimBeforeCleanupEvent e) {
        for (ElectricVehicle electricVehicle : electricFleet.getElectricVehicles().values()) {
            Id<VehicleType> typeId = Id.create(electricVehicle.getVehicleType(), VehicleType.class);
            //assume the vehicle type to be existing and throw NullPointer if not
           // EVUtils.setInitialEnergy(vehicles.getVehicleTypes().get(typeId).getEngineInformation(), EvUnits.J_to_kWh(electricVehicle.getBattery().getSoc()));
         //   this is to do not update the SOC
            spec.replaceVehicleSpecification(ImmutableElectricVehicleSpecification.newBuilder()
							.id(electricVehicle.getId())
							.batteryCapacity(electricVehicle.getBattery().getCapacity())
							.initialSoc(electricVehicle.getBattery().getSoc())
							.chargerTypes(electricVehicle.getChargerTypes())
							.vehicleType(typeId.toString())
							.build());
        }
    }
}

class FinalSoc2VehicleTypeProvider implements Provider<MobsimListener> {
    @Inject
    ElectricFleet electricFleet;
    @Inject
    Scenario scenario;
    @Inject
    ElectricFleetSpecification spec;


    @Override
    public MobsimListener get() {
        if(!scenario.getConfig().qsim().getVehiclesSource().equals(QSimConfigGroup.VehiclesSource.fromVehiclesData)){
            throw new IllegalArgumentException("transfer of final soc to the vehicle type is only possible if every agent uses its own vehicle type!! That means you have to set" +
                    "QSimConfigGroup.VehiclesSource.fromVehiclesData and specify one vehicle type per mode per agent! ");
        }

        return new FinalSoc2VehicleType(electricFleet, scenario.getVehicles(),spec);
    }
}
