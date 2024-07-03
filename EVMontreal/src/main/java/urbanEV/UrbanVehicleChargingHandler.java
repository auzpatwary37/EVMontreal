/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package urbanEV;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.contrib.ev.charging.ChargingEndEvent;
import org.matsim.contrib.ev.charging.ChargingEndEventHandler;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStartEventHandler;
import org.matsim.contrib.ev.charging.VehicleChargingHandler;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructure;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructures;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.ImmutableListMultimap;

import binding.ChargingWithQueueingLogic;


/**
 * This is an events based approach to trigger vehicle charging. Vehicles will be charged as soon as a person begins a PLUGIN_INTERACTION activity.
 * Charging will end as soon as the person performs a PLUGOUT_INTERACTION activity.
 * <p>
 * Do not use this class for charging DVRP vehicles (DynAgents). In that case, vehicle charging is simulated with ChargingActivity (DynActivity)
 * <p>
 *
 * This class is a modified version of {@link VehicleChargingHandler}
 *
 * @author tschlenther
 */
public class UrbanVehicleChargingHandler
		implements ActivityStartEventHandler, ActivityEndEventHandler, PersonLeavesVehicleEventHandler,
		ChargingEndEventHandler, ChargingStartEventHandler,MobsimScopeEventHandler  {

	public static final String PLUGIN_IDENTIFIER = " plugin";
	public static final String PLUGIN_INTERACTION = PlanCalcScoreConfigGroup.createStageActivityType(
			PLUGIN_IDENTIFIER);
	public static final String PLUGOUT_IDENTIFIER = " plugout";
	public static final String PLUGOUT_INTERACTION = PlanCalcScoreConfigGroup.createStageActivityType(
			PLUGOUT_IDENTIFIER);
	private final Map<Id<Person>, Id<Vehicle>> lastVehicleUsed = new ConcurrentHashMap<>();
	private final Map<Id<Vehicle>, Id<Charger>> vehiclesAtChargers = new ConcurrentHashMap<>();

	private final ChargingInfrastructure chargingInfrastructure;
	private final ElectricFleet electricFleet;
	private final ImmutableListMultimap<Id<Link>, Charger> chargersAtLinks;
	
	private Map<Id<ElectricVehicle>,Id<Charger>> chargersToVehicleMap = new ConcurrentHashMap<>();

	
	
	private Map<Id<Link>, Map<Id<Person>, chargingInfo>> chargingProcedures = new HashMap<>();
	@Inject 
	Scenario scenario;
	@Inject
	UrbanVehicleChargingHandler(ChargingInfrastructure chargingInfrastructure, ElectricFleet electricFleet) {
		this.chargingInfrastructure = chargingInfrastructure;
		this.electricFleet = electricFleet;
		this.chargersAtLinks = ChargingInfrastructures.getChargersAtLinks(chargingInfrastructure);
	}

	/**
	 * This assumes no liability which charger is used, as long as the type matches
	 *
	 * @param event
	 */
	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (event.getActType().endsWith(PLUGIN_INTERACTION)) {
			Id<Vehicle> vehicleId = lastVehicleUsed.get(event.getPersonId());
			if (vehicleId != null) {
				Id<ElectricVehicle> evId = Id.create(vehicleId, ElectricVehicle.class);
				if (electricFleet.getElectricVehicles().containsKey(evId)) {
					ElectricVehicle ev = electricFleet.getElectricVehicles().get(evId);
					List<Charger> chargers = chargersAtLinks.get(event.getLinkId());
					Charger charger = null;
					try {
					 charger = chargers.stream()
							.filter(ch -> ev.getChargerTypes().contains(ch.getChargerType()))

							.findAny()
							.get();
					}catch(Exception e) {
						System.out.println();	
					}
					charger.getLogic().addVehicle(ev, event.getTime());
					
					
					this.chargersToVehicleMap.put(evId, charger.getId());
					
					Map<Id<Person>, chargingInfo> proceduresOnLink = this.chargingProcedures.get(event.getLinkId());
					
					if(proceduresOnLink != null && proceduresOnLink.containsKey(event.getPersonId())){
//						Plan plan = null;
//						System.out.println(this.scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan());
						Person person = scenario.getPopulation().getPersons().get(event.getPersonId());
						Plan plan = scenario.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
						throw new RuntimeException("person " + event.getPersonId() + " tries to charge 2 vehicles at the same time on link " + event.getLinkId() +
								". this is not supported.");
					} else if(proceduresOnLink == null) {
						proceduresOnLink = new HashMap<>();
					}
					proceduresOnLink.put(event.getPersonId(), new chargingInfo(event.getTime(), event.getPersonId(), charger.getLink().getId(), charger.getId(),evId));
					this.chargingProcedures.put(event.getLinkId(), proceduresOnLink);
				} else {
					throw new IllegalStateException("can not plug in non-registered ev " + evId + " of person " + event.getPersonId());
				}
			} else {
				throw new IllegalStateException("last used vehicle  of person " + event.getPersonId() + "is null. should not happen");
			}
		}
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (event.getActType().endsWith(PLUGOUT_INTERACTION)) {
			chargingInfo tuple = chargingProcedures.get(event.getLinkId()).remove(event.getPersonId());
			if (tuple != null) {
				Id<ElectricVehicle> evId = tuple.vehicleId;
//				if(vehiclesAtChargers.remove(evId) != null){ //if null, vehicle is fully charged and de-plugged already or the vehicle never got plugged in and still in the queue (see handleEvent(ChargingEndedEvent) )
//					Id<Charger> chargerId = tuple.chargerId;
//					Charger c = chargingInfrastructure.getChargers().get(chargerId);
//					if(c.getLogic().getPluggedVehicles().contains(electricFleet.getElectricVehicles().get(evId)))c.getLogic().removeVehicle(electricFleet.getElectricVehicles().get(evId), event.getTime());
//				}else {////________________changed code
//					Id<Charger> chargerId = tuple.chargerId;
//					Charger c = chargingInfrastructure.getChargers().get(chargerId);
//					if(c.getLogic().getQueuedVehicles().contains(electricFleet.getElectricVehicles().get(evId)))c.getLogic().removeVehicle(electricFleet.getElectricVehicles().get(evId), event.getTime());
//				}////________________________________________
				
				if(this.chargersToVehicleMap.remove(evId)!=null) {
					vehiclesAtChargers.remove(evId);
					Id<Charger> chargerId = tuple.chargerId;
					Charger c = chargingInfrastructure.getChargers().get(chargerId);
					c.getLogic().removeVehicle(electricFleet.getElectricVehicles().get(evId), event.getTime());
				}else {
					throw new IllegalArgumentException("Vehicle was never plugged in!!!!");
				}
				
			} else {
				throw new RuntimeException("there is something wrong with the charging procedure of person=" + event.getPersonId() + " on link= " + event.getLinkId());
			}
		}
	}
	@Override
	public void reset(int i) {
		this.chargingProcedures.clear();
		vehiclesAtChargers.clear();
		lastVehicleUsed.clear();
		this.chargingInfrastructure.getChargers().entrySet().forEach(c->((ChargingWithQueueingLogic)c.getValue().getLogic()).reset(i));
	}
	
	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		lastVehicleUsed.put(event.getPersonId(), event.getVehicleId());
	}

	@Override
	public void handleEvent(ChargingEndEvent event) {
		vehiclesAtChargers.remove(event.getVehicleId());
		//Charging has ended before activity ends
	}

	@Override
	public void handleEvent(ChargingStartEvent event) {
		vehiclesAtChargers.put(event.getVehicleId(), event.getChargerId());
		//Charging has started
	}
	

}

class chargingInfo {
	final Id<Person> personId;
	final Id<Link> linkId;
	final Id<Charger> chargerId;
	final Id<ElectricVehicle>vehicleId;
	final double plugInTime;
	double plugOutTime;
	public chargingInfo(double plugInTime, Id<Person> personId, Id<Link> linkId, Id<Charger> chargerId, Id<ElectricVehicle> vehicleId) {
		this.personId = personId;
		this.linkId = linkId;
		this.chargerId = chargerId;
		this.vehicleId = vehicleId;
		this.plugInTime = plugInTime;
		
	}
}
