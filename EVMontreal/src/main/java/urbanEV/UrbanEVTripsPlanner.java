/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Provider;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.common.util.StraightLineKnnFinder;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.MobsimInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimInitializedListener;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.StageActivityTypeIdentifier;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.withinday.utils.EditPlans;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.sun.istack.Nullable;

import EVPricing.ChargerPricingProfiles;
import one.util.streamex.StreamEx;

class UrbanEVTripsPlanner implements MobsimInitializedListener {

	@Inject
	protected Provider<TripRouter> tripRouterProvider;

	@Inject
	Scenario scenario;

	@Inject
	Vehicles vehicles;

	@Inject
	protected SingleModeNetworksCache singleModeNetworksCache;

	@Inject
	private ElectricFleetSpecification electricFleetSpecification;

	@Inject
	protected ChargingInfrastructureSpecification chargingInfrastructureSpecification;

	@Inject
	protected DriveEnergyConsumption.Factory driveConsumptionFactory;

	@Inject
	protected AuxEnergyConsumption.Factory auxConsumptionFactory;

	@Inject
	protected ChargingPower.Factory chargingPowerFactory;

	@Inject
	private ChargingLogic.Factory chargingLogicFactory;

	@Inject
	private Map<String, TravelTime> travelTimes;

	@Inject
	ActivityWhileChargingFinder activityWhileChargingFinder;

	@Inject
	OutputDirectoryHierarchy controlerIO;
	@Inject
	IterationCounter iterationCounter;

	@Inject
	Config config;
	
	@Inject 
	private ChargerPricingProfiles chargerPricingProfiles;
	
	@Inject
	private TimeInterpretation time;


	protected QSim qsim;

	protected static final Logger log = Logger.getLogger(UrbanEVTripsPlanner.class);
	protected static List<PersonContainer2> personContainer2s = new ArrayList<>();

	@Override
	public void notifyMobsimInitialized(MobsimInitializedEvent e) {
		if (!(e.getQueueSimulation() instanceof QSim)) {
			throw new IllegalStateException(UrbanEVTripsPlanner.class.toString() + " only works with a mobsim of type " + QSim.class);
		}
		//collect all selected plans that contain ev legs and map them to the set of ev used
		Map<Plan, Set<Id<Vehicle>>> selectedEVPlans = StreamEx.of(scenario.getPopulation().getPersons().values())
				.mapToEntry(p -> p.getSelectedPlan(), p -> getUsedEV(p.getSelectedPlan()))
				.filterValues(evSet -> !evSet.isEmpty())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
//		Map<Plan, Set<Id<Vehicle>>> selectedEVPlans = new HashMap<>();
//		scenario.getPopulation().getPersons().values().parallelStream().forEach(p->{
//			Plan pl = p.getSelectedPlan();
//			Set<Id<Vehicle>> usedEV = getUsedEV(p.getSelectedPlan());
//			if(!usedEV.isEmpty())selectedEVPlans.put(pl, usedEV);
//		});
		
		this.qsim = (QSim) e.getQueueSimulation();
		processPlans(selectedEVPlans);
		CSVPrinter csvPrinter;
		try {
			csvPrinter = new CSVPrinter(Files.newBufferedWriter(Paths.get(controlerIO.getIterationFilename(iterationCounter.getIterationNumber(), "outOfEnergy.csv"))), CSVFormat.DEFAULT.withDelimiter(';').
					withHeader("PersonID", "Reason"));

			{
				for (PersonContainer2 personContainer2 : personContainer2s) {


					csvPrinter.printRecord(personContainer2.personId, personContainer2.reason);
				}
			}




			csvPrinter.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	personContainer2s.clear();
	}

	/**
	 * retrieve all used EV in the given plan
	 *
	 * @param plan
	 * @return
	 */
	protected Set<Id<Vehicle>> getUsedEV(Plan plan) {
		return TripStructureUtils.getLegs(plan).stream().filter(leg->leg.getMode().equals("car")||leg.getMode().equals("car_passenger"))
				.map(leg -> VehicleUtils.getVehicleId(plan.getPerson(), leg.getMode()))
				.filter(vehicleId -> isEV(vehicleId))
				.collect(toSet());
//		Set<Id<Vehicle>> vs = new HashSet<>();
//		TripStructureUtils.getLegs(plan).stream().forEach(l->{
//			if(l.getMode().equals("car")&&l.getMode().equals("car_passenger")) {
//				Id<Vehicle> v = VehicleUtils.getVehicleId(plan.getPerson(), l.getMode());
//				if(isEV(v))vs.add(v);
//			}
//		});
//		return vs;
	}

	protected boolean isEV(Id<Vehicle> vehicleId) {
		return this.electricFleetSpecification.getVehicleSpecifications().containsKey(getWrappedElectricVehicleId(vehicleId));
	}

	protected void processPlans(Map<Plan, Set<Id<Vehicle>>> selectedEVPlans) {


		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);

//		selectedEVPlans.entrySet().parallelStream().forEach(pl->{
//			
//		Plan plan = pl.getKey();
		for (Plan plan : selectedEVPlans.keySet()) {

			//from here we deal with the modifiable plan (only!?)

			MobsimAgent mobsimagent = qsim.getAgents().get(plan.getPerson().getId());
			Plan modifiablePlan = WithinDayAgentUtils.getModifiablePlan(mobsimagent);
			TripRouter tripRouter = tripRouterProvider.get();
			Set<String> modesWithVehicles = new HashSet<>(scenario.getConfig().qsim().getMainModes());
			modesWithVehicles.addAll(scenario.getConfig().plansCalcRoute().getNetworkModes());

		//	for(Id<Vehicle> ev: pl.getValue()) {
			for (Id<Vehicle> ev : selectedEVPlans.get(plan)) {
				//only replan cnt times per vehicle and person. otherwise, there might be a leg which is just too long and we end up in an infinity loop...
				int cnt = configGroup.getMaximumChargingProceduresPerAgent();
//				boolean pluginAtHomeBeforeMobSim = configGroup.getPluginAtHomeBeforeMobSim;
				/*
				 * i had all of this implemented without so many if-statements and without do-while-loop. However, i felt like when replanning takes place, we need to start
				 * consumption estimation all over. The path to avoid this would be by complicated date/method structure, which would also be bad (especially to maintain...)
				 * ts, nov' 27, 2020
				 */
				ElectricVehicleSpecification electricVehicleSpecification = electricFleetSpecification.getVehicleSpecifications()
						.get(getWrappedElectricVehicleId(ev));
				Leg legWithCriticalSOC;
				ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);;
			
				List <Leg> evCarLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(TransportMode.car)).collect(toList());
//				List <Leg> evCarLegs = new ArrayList<>();
//				TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
//					if(l.getMode().equals(TransportMode.car))evCarLegs.add(l);
//					});
				boolean pluginBeforeStart = configGroup.getPluginBeforeStartingThePlan();

				if(pluginBeforeStart && hasHomeCharger(mobsimagent, modifiablePlan, evCarLegs, pseudoVehicle) && modifiablePlan.getPlanElements().indexOf(evCarLegs.get(0))==1){ //TODO potentially check for activity duration and/or SoC

					Leg firstEvLeg = evCarLegs.get(0);
//					Activity originalActWhileCharging = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));//
//					Activity lastAct = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));
					Activity actWhileCharging = (Activity) modifiablePlan.getPlanElements().get(0);
					actWhileCharging =  EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(firstEvLeg));// comment this line out TODO: figure out this line 
					Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(firstEvLeg.getMode());
					Link chargingLink = modeNetwork.getLinks().get(actWhileCharging.getLinkId());
					String routingMode = TripStructureUtils.getRoutingMode(firstEvLeg);
					planPluginTripFromHomeToCharger(modifiablePlan, routingMode, electricVehicleSpecification, actWhileCharging, chargingLink, tripRouter);
					Leg plugoutLeg = firstEvLeg;
					Activity plugoutTripOrigin = findRealOrChargingActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
					Activity plugoutTripDestination = findRealOrChargingActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
					planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(plugoutTripOrigin, modifiablePlan).seconds());
					//TODO don't we need to trigger SoC emulation here (in order to account for the energy charged before home actvity is ended?) see also todo-comment below
				}
				

				do {
					//double newSoC = EVUtils.getInitialEnergy(vehicles.getVehicles().get(ev).getType().getEngineInformation())* EvUnits.J_PER_kWh; //TODO is this correct if vehicle was plugged in before start (sse above) ?
					//pseudoVehicle.getBattery().setSoc(newSoC);
					double capacityThreshold = pseudoVehicle.getBattery().getCapacity() * (configGroup.getCriticalRelativeSOC());
					legWithCriticalSOC = getCriticalOrLastEvLeg(modifiablePlan, pseudoVehicle, ev);
					String mode = legWithCriticalSOC.getMode();
					List <Leg> evLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(leg -> leg.getMode().equals(mode)).collect(toList());
//					List <Leg> evLegs = new ArrayList<>();
//					TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
//						if(l.getMode().equals(mode))evLegs.add(l);
//					});
					if (legWithCriticalSOC != null) {

						if (evLegs.get(0).equals(legWithCriticalSOC)) {
							log.warn("SoC of Agent" + mobsimagent.getId() + "is running beyond capacity threshold during the first leg of the day.");
							PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "is running beyond capacity threshold during the first leg of the day.");
							personContainer2s.add(personContainer2);
							break;
						}

						else if (evLegs.get(evLegs.size()-1).equals(legWithCriticalSOC) && isHomeChargingTrip(mobsimagent, modifiablePlan, evLegs, pseudoVehicle) && pseudoVehicle.getBattery().getSoc() > 0) {

							//trip leads to location of the first activity in the plan and there is a charger and so we can charge at home do not search for opportunity charge before
							Activity actBefore = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC));
							Activity lastAct = EditPlans.findRealActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(legWithCriticalSOC));
							
							Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(legWithCriticalSOC.getMode());
							Link chargingLink = modeNetwork.getLinks().get(lastAct.getLinkId());
							String routingMode = TripStructureUtils.getRoutingMode(legWithCriticalSOC);

							planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBefore, lastAct, chargingLink, tripRouter);
							log.info(mobsimagent.getId() + " is charging at home.");
							PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "is charging at home.");
							personContainer2s.add(personContainer2);
							break;

						} else if( evLegs.get(evLegs.size()-1).equals(legWithCriticalSOC) && pseudoVehicle.getBattery().getSoc() > capacityThreshold ){
							cnt = 0;

						} else {
							replanPrecedentAndCurrentEVLegs(mobsimagent, modifiablePlan, electricVehicleSpecification, legWithCriticalSOC);
							log.info(mobsimagent.getId() + " is charging on the route.");
							PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "is charging on the route.");
							personContainer2s.add(personContainer2);
							cnt--;
						}
					} else {
						throw new IllegalStateException("critical leg is null. should not happen");
					}

				} while (legWithCriticalSOC != null && cnt > 0);
			}
			if(!isConsistant(modifiablePlan)) {
				System.out.println("Plan is not consistant!!! Debug!!!");
			}
		}
			
		//});
	}
	
	
	protected boolean isConsistant(Plan plan) {
		List<Activity> acts = new ArrayList<>();
//		plan.getPlanElements().stream().filter(pe-> pe instanceof Activity).forEach(a->acts.add(((Activity)a)));
		plan.getPlanElements().stream().forEach(a->{
			if(a instanceof Activity)acts.add(((Activity)a));
			});
		int charging = 0;
		for(Activity a:acts) {
			if(a.getType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER))charging++;
			else if(a.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER))charging--;
			if(charging>1)return false;
		}
		
		return true;
	}

	/**
	 * returns leg for which the critical soc is exceeded or the last of all ev legs.
	 *
	 * @param modifiablePlan
	 * @param pseudoVehicle
	 * @param originalVehicleId
	 * @return
	 */
	protected Leg getCriticalOrLastEvLeg(Plan modifiablePlan, ElectricVehicle pseudoVehicle, Id<Vehicle> originalVehicleId) {
		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);


		double capacityThreshold = pseudoVehicle.getBattery().getCapacity() * (configGroup.getCriticalRelativeSOC()); //TODO randomize? Might also depend on the battery size!

		Double chargingBegin = null;

		Set<String> modesWithVehicles = new HashSet<>(scenario.getConfig().qsim().getMainModes());
		modesWithVehicles.addAll(scenario.getConfig().plansCalcRoute().getNetworkModes());

		Leg lastLegWithVehicle = null;
		//TODO: The current logic will go through each leg and discharge the ev for that leg; and then will check if the soc is lower than capacity threshold.
		//This does not ensure that the remaining soc is enough for completing the next leg, resulting in some en route dead evs. 
		//This should be fixed/ 
		//double socAfterLeg = pseudoVehicle.getBattery().getSoc();
		
		for (PlanElement planElement : modifiablePlan.getPlanElements()) {
			if (planElement instanceof Leg) {

				Leg leg = (Leg) planElement;
				if (modesWithVehicles.contains(leg.getMode()) && VehicleUtils.getVehicleId(modifiablePlan.getPerson(), leg.getMode()).equals(originalVehicleId)) {
					lastLegWithVehicle = leg;
					emulateVehicleDischarging(pseudoVehicle, leg);
					if (pseudoVehicle.getBattery().getSoc() <= capacityThreshold) {
						return leg;
					}
				}
			} else if (planElement instanceof Activity) {
				if (((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION)) {
					Leg legToCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) - 1);
					chargingBegin = legToCharger.getDepartureTime().seconds() + legToCharger.getTravelTime().seconds();

				} else if (((Activity) planElement).getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {

					Leg legFromCharger = (Leg) modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(planElement) + 1);
					if (chargingBegin == null) throw new IllegalStateException();
					double chargingDuration = legFromCharger.getDepartureTime().seconds() - chargingBegin;

					ChargerSpecification chargerSpecification = null;
							
					Optional<ChargerSpecification> a = chargingInfrastructureSpecification.getChargerSpecifications()
							.values()
							.stream()
							.filter(charger -> charger.getLinkId().equals(((Activity) planElement).getLinkId()))
							.filter(charger -> pseudoVehicle.getChargerTypes().contains(charger.getChargerType()))
							.findAny();

					if( !a.isPresent()) {
						throw new NoSuchElementException();
					}else {
						chargerSpecification = a.get();
					}
//					List<ChargerSpecification> chargers = new ArrayList<>();
//					chargingInfrastructureSpecification.getChargerSpecifications().values().stream().forEach(c->{
//						if(c.getLinkId().equals(((Activity) planElement).getLinkId()) && pseudoVehicle.getChargerTypes().contains(c.getChargerType())){
//							chargers.add(c);
//						}
//					});
//					
//					if(chargers.isEmpty()) {
//						throw new NoSuchElementException();
//					}else {
//						chargerSpecification = chargers.get(MatsimRandom.getRandom().nextInt(chargers.size()));
//					}
					
					
					pseudoVehicle.getBattery().setCharge(pseudoVehicle.getBattery().getCharge() +pseudoVehicle.getChargingPower().calcChargingPower(chargerSpecification) * chargingDuration);
				}
			} else throw new IllegalArgumentException();
		}

		return lastLegWithVehicle;
	}
	public Id<ElectricVehicle> getWrappedElectricVehicleId(Id<Vehicle> vehicleId){
		Id<ElectricVehicle> eId = Id.create(vehicleId.toString(),ElectricVehicle.class);
		return eId;
	}

	/**
	 * @param mobsimagent
	 * @param modifiablePlan
	 * @param electricVehicleSpecification
	 * @param leg the critical Leg
	 */
	protected void replanPrecedentAndCurrentEVLegs(MobsimAgent mobsimagent, Plan modifiablePlan, ElectricVehicleSpecification electricVehicleSpecification, Leg leg) {
		Network modeNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());

		String routingMode = TripStructureUtils.getRoutingMode(leg);
		List <Leg> evLegs = TripStructureUtils.getLegs(modifiablePlan).stream().filter(evleg -> evleg.getMode().equals(routingMode)).collect(toList());
//		List <Leg> evLegs = new ArrayList<>();
//		TripStructureUtils.getLegs(modifiablePlan).stream().forEach(l->{
//			if(l.getMode().equals(routingMode))evLegs.add(l);
//		});
		int legIndex = modifiablePlan.getPlanElements().indexOf(leg);
		Preconditions.checkState(legIndex > -1, "could not locate leg in plan");
		Activity actWhileCharging;
		ChargerSpecification selectedCharger;
		int legIndexCounter = legIndex;
		//find suitable non-stage activity before SOC threshold passover
		do {
			actWhileCharging = activityWhileChargingFinder.findActivityWhileChargingBeforeLeg(mobsimagent, modifiablePlan, (Leg) modifiablePlan.getPlanElements().get(legIndexCounter));
			if (actWhileCharging == null){
				log.warn(mobsimagent + " can't find a suitable activity prior the critical leg!");
				PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "can't find a suitable activity prior the critical leg!");
				personContainer2s.add(personContainer2);
				return;
			}
			selectedCharger = selectChargerNearToLink(mobsimagent.getId(),actWhileCharging.getLinkId(), electricVehicleSpecification, modeNetwork);

			if(selectedCharger == null){

				leg = evLegs.get(evLegs.indexOf(leg)-1);
				legIndexCounter = modifiablePlan.getPlanElements().indexOf(leg);
			}
		} while (actWhileCharging != null && selectedCharger == null);


//		Preconditions.checkNotNull(actWhileCharging, "could not insert plugin activity in plan of agent " + mobsimagent.getId() +
//				".\n One reason could be that the agent has no suitable activity prior to the leg for which the " +
//				" energy threshold is expected to be exceeded. \n" +
//				" Another reason  might be that it's vehicle is running beyond energy threshold during the first leg of the day." +
//				"That could possibly be avoided by using EVNetworkRoutingModule..."); //TODO let the sim just run and let the ev run empty!?

		//TODO what if actWhileCharging does not hold a link id?

		Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());

//		Activity pluginTripOrigin = EditPlans.findRealActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(actWhileCharging));
		Activity pluginTripOrigin = findRealOrChargingActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(actWhileCharging));

		Leg plugoutLeg = activityWhileChargingFinder.getNextLegOfRoutingModeAfterActivity(ImmutableList.copyOf(modifiablePlan.getPlanElements()), actWhileCharging, routingMode);
		Activity plugoutTripOrigin = findRealOrChargingActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
		Activity plugoutTripDestination = findRealOrChargingActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));

//		{    //some consistency checks.. //TODO consider to put in a JUnit test..
//			Preconditions.checkNotNull(pluginTripOrigin, "pluginTripOrigin is null. should never happen..");
//			Preconditions.checkState(!pluginTripOrigin.equals(actWhileCharging), "pluginTripOrigin is equal to actWhileCharging. should never happen..");
//
//			PlanElement legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) + 3);
//			Preconditions.checkState(legToBeReplaced instanceof Leg);
//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after pluginTripOrigin has the wrong routing mode. should not happen..");
//
//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(actWhileCharging) - 3);
//			Preconditions.checkState(legToBeReplaced instanceof Leg);
//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before actWhileCharging has the wrong routing mode. should not happen..");
//
//			Preconditions.checkState(!plugoutTripDestination.equals(actWhileCharging), "plugoutTripDestination is equal to actWhileCharging. should never happen..");
//
//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(pluginTripOrigin) < modifiablePlan.getPlanElements().indexOf(actWhileCharging));
//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(actWhileCharging) <= modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin));
//			Preconditions.checkState(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) < modifiablePlan.getPlanElements().indexOf(plugoutTripDestination));
//
//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripOrigin) + 3);
//			Preconditions.checkState(legToBeReplaced instanceof Leg);
//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg after plugoutTripOrigin has the wrong routing mode. should not happen..");
//
//			legToBeReplaced = modifiablePlan.getPlanElements().get(modifiablePlan.getPlanElements().indexOf(plugoutTripDestination) - 3);
//			Preconditions.checkState(legToBeReplaced instanceof Leg);
//			Preconditions.checkState(TripStructureUtils.getRoutingMode((Leg) legToBeReplaced).equals(routingMode), "leg before plugoutTripDestination has the wrong routing mode. should not happen..");
//		}

		TripRouter tripRouter = tripRouterProvider.get();
		planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, actWhileCharging, chargingLink, tripRouter);
		planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, plugoutTripOrigin, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(plugoutTripOrigin, modifiablePlan).seconds());

		if(!isConsistant(modifiablePlan)) {
			System.out.println("Plan is not consistant!!! Debug!!!");
		}
	}
/**
 * 
 * @param plan
 * @param routingMode is the mode of the ev
 * @param electricVehicleSpecification
 * @param origin the location of the activity before the ev leg
 * @param destination the location of the activity after the ev leg
 * @param chargingLink 
 * @param tripRouter
 * @param now is the time the origin activity finishes
 */
	protected void planPlugoutTrip(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification, Activity origin, Activity destination, Link chargingLink, TripRouter tripRouter, double now) {
		Facility fromFacility = FacilitiesUtils.toFacility(origin, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(destination, scenario.getActivityFacilities());

		List<? extends PlanElement> routedSegment;
		//actually destination can not be null based on how we determine the actWhileCharging = origin at the moment...
		if (destination == null) throw new RuntimeException("should not happen");

		List<PlanElement> trip = new ArrayList<>();

		//add leg to charger
		routedSegment = tripRouter.calcRoute(TransportMode.walk, fromFacility, chargerFacility,
				now, plan.getPerson(),null);
		Leg accessLeg = (Leg) routedSegment.get(0);
		now = time.decideOnElementEndTime(accessLeg, now).seconds();
		TripStructureUtils.setRoutingMode(accessLeg, routingMode);//TODO: Should not the routing mode here be walk???
		trip.add(accessLeg);

		//add plugout act
		Activity plugOutAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGOUT_IDENTIFIER);// this is how to plan a plugout activity
		trip.add(plugOutAct);
		now = time.decideOnElementEndTime(plugOutAct, now).seconds();

		//add leg to destination
		routedSegment = tripRouter.calcRoute(routingMode, chargerFacility, toFacility, now, plan.getPerson(),null);
		trip.addAll(routedSegment);

		for (PlanElement element : routedSegment) {
			now = time.decideOnElementEndTime(element, now).seconds();
			//insert vehicle id
			if(element instanceof Leg){
				Leg leg = (Leg) element;
				if(leg.getMode().equals(routingMode)){
					NetworkRoute route = ((NetworkRoute) leg.getRoute());
					if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
				}
			}
		}

		//insert trip
		TripRouter.insertTrip(plan, origin, trip, destination);

		//reset activity end time
		if (!plan.getPlanElements().get(plan.getPlanElements().size() - 1).equals(destination)) {
			destination.setEndTime(time.decideOnActivityEndTime(destination, now).seconds());
		}
	}
/**
 * 
 * @param plan
 * @param routingMode mode for the EV leg
 * @param electricVehicleSpecification
 * @param actBeforeCharging 
 * @param actWhileCharging activity after running
 * @param chargingLink
 * @param tripRouter
 */
	protected void planPluginTrip(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification, Activity actBeforeCharging, Activity actWhileCharging, Link chargingLink, TripRouter tripRouter) {
		Facility fromFacility = FacilitiesUtils.toFacility(actBeforeCharging, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());

		List<PlanElement> trip = new ArrayList<>();
		//add leg to charger
		List<? extends PlanElement> routedSegment = tripRouter.calcRoute(routingMode, fromFacility, chargerFacility,
				time.decideOnActivityEndTimeAlongPlan(actBeforeCharging, plan).seconds(), plan.getPerson(),null);

		//set the vehicle id
		for (Leg leg : TripStructureUtils.getLegs(routedSegment)) {
			if(leg.getMode().equals(routingMode)){
				NetworkRoute route = ((NetworkRoute) leg.getRoute());
				if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
			}
		}

		Leg lastLeg = (Leg) routedSegment.get(routedSegment.size() - 1);
		double now = lastLeg.getDepartureTime().seconds() + lastLeg.getRoute().getTravelTime().seconds();
		trip.addAll(routedSegment);

		//add plugin act
		Activity pluginAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);
		trip.add(pluginAct);

		now = time.decideOnElementEndTime(pluginAct, now).seconds();

		//add walk leg to destination
		routedSegment = tripRouter.calcRoute(TransportMode.walk, chargerFacility, toFacility, now, plan.getPerson(),null);
		Leg egress = (Leg) routedSegment.get(0);
		TripStructureUtils.setRoutingMode(egress, routingMode);// should not it be walk???
		trip.add(egress);
		now = time.decideOnElementEndTime(egress, now).seconds();

		//insert trip
		TripRouter.insertTrip(plan, actBeforeCharging, trip, actWhileCharging);

		//reset activity end time
		if (!plan.getPlanElements().get(plan.getPlanElements().size()-1).equals(actWhileCharging)) {
			actWhileCharging.setEndTime(time.decideOnActivityEndTime(actWhileCharging, now).seconds());
		}
	}


	//TODO possibly put behind interface
	@Nullable
	protected ChargerSpecification selectChargerNearToLink(Id<Person>pId, Id<Link> linkId, ElectricVehicleSpecification vehicleSpecification, Network network) {

		UrbanEVConfigGroup configGroup = (UrbanEVConfigGroup) config.getModules().get(UrbanEVConfigGroup.GROUP_NAME);
		double maxDistanceToAct = configGroup.getMaxDistanceBetweenActAndCharger_m();

		List<ChargerSpecification> chargerList = chargingInfrastructureSpecification.getChargerSpecifications()
				.values()
				.stream()
				.filter(charger -> vehicleSpecification.getChargerTypes().contains(charger.getChargerType()))
				.filter(c->this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().isEmpty()||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().contains(pId))
				.collect(Collectors.toList());

//		List<ChargerSpecification> chargerList = new ArrayList<>();
//		chargingInfrastructureSpecification.getChargerSpecifications()
//		.values()
//		.stream().forEach(c->{
//			if(vehicleSpecification.getChargerTypes().contains(c.getChargerType()) && 
//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().isEmpty() ||
//							this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId()).getPersonsAccecibleTo().contains(pId))) {
//				chargerList.add(c);
//			}
//		});
		
		StraightLineKnnFinder<Link, ChargerSpecification> straightLineKnnFinder = new StraightLineKnnFinder<>(
				6, l -> l.getFromNode().getCoord(), s -> network.getLinks().get(s.getLinkId()).getToNode().getCoord()); //TODO get closest X chargers and choose randomly?
		List<ChargerSpecification> nearestChargers = straightLineKnnFinder.findNearest(network.getLinks().get(linkId),chargerList.stream());
		List<ChargerSpecification>nearestChargersWithinLimit = nearestChargers.stream().filter(c->NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getCoord(), network.getLinks().get(c.getLinkId()).getCoord())<maxDistanceToAct).collect(Collectors.toList()); 
		if (nearestChargersWithinLimit.isEmpty()) {
			throw new RuntimeException("no charger could be found for vehicle type " + vehicleSpecification.getMatsimVehicle().getType().getId());
		}
		//double distanceFromActToCharger = NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getToNode().getCoord(), network.getLinks().get(nearestChargers.get(0).getLinkId()).getToNode().getCoord());
//		if (distanceFromActToCharger >= maxDistanceToAct) {
//			return null;
//		}
//			//throw new RuntimeException("There are no chargers within 1000m");
//			log.warn("Charger out of range. Inefficient charging " + NetworkUtils.getEuclideanDistance(network.getLinks().get(linkId).getToNode().getCoord(), network.getLinks().get(nearestChargers.get(0).getLinkId()).getToNode().getCoord()));
//		}
		else{
			int rand = MatsimRandom.getRandom().nextInt(nearestChargersWithinLimit.size());//These two lines applies random charger selection. Delete these two lines and uncomment the commented line to get back to the original version. 
			return nearestChargersWithinLimit.get(rand);
			//return nearestChargers.get(0);
			
		}
	}

	/**
	 * this method has the side effect that the soc of the ev is altered by estimated energy consumption of the leg
	 *
	 * @param ev
	 * @param leg
	 */
	protected void emulateVehicleDischarging(ElectricVehicle ev, Leg leg) {
		//retrieve mode specific network
		Network network = this.singleModeNetworksCache.getSingleModeNetworksCache().get(leg.getMode());
		//retrieve routin mode specific travel time
		String routingMode = TripStructureUtils.getRoutingMode(leg);
		TravelTime travelTime = this.travelTimes.get(routingMode);
		if (travelTime == null) {
			throw new RuntimeException("No TravelTime bound for mode " + routingMode + ".");
		}

//		Map<Link, Double> consumptions = new LinkedHashMap<>();
		NetworkRoute route = (NetworkRoute) leg.getRoute();
		List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());

		DriveEnergyConsumption driveEnergyConsumption = ev.getDriveEnergyConsumption();
		AuxEnergyConsumption auxEnergyConsumption = ev.getAuxEnergyConsumption();
		double linkEnterTime = leg.getDepartureTime().seconds();

		for (Link l : links) {
			double travelT = travelTime.getLinkTravelTime(l, leg.getDepartureTime().seconds(), null, null);

			double driveConsumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime);
			double auxConsumption = auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
//			double consumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime)
//					+ auxEnergyConsumption.calcEnergyConsumption(leg.getDepartureTime().seconds(), travelT, l.getId());
			double consumption = driveConsumption + auxConsumption;
			ev.getBattery().setCharge(ev.getBattery().getCharge()-consumption);
			linkEnterTime += travelT;
		}
	}

//	the following methods are modified versions of EditPlans.findRealActBefore() and EditPlans.findRealActAfter()

	protected Activity findRealOrChargingActBefore(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent);
		List<PlanElement> planElements = plan.getPlanElements();

		Activity prevAct = null;
		for (int ii = 0; ii < index; ii++) {
			if (planElements.get(ii) instanceof Activity) {
				Activity act = (Activity) planElements.get(ii);
				if (!StageActivityTypeIdentifier.isStageActivity(act.getType()) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {
					prevAct = act;
				}
			}
		}
		return prevAct;
	}

	protected Activity findRealOrChargingActAfter(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent);
		List<PlanElement> planElements = plan.getPlanElements();
		return (Activity) planElements.get(findIndexOfRealActAfter(agent, index));
	}

	protected int findIndexOfRealActAfter(MobsimAgent agent, int index) {
		Plan plan = WithinDayAgentUtils.getModifiablePlan(agent);
		List<PlanElement> planElements = plan.getPlanElements();

		int theIndex = -1;
		for (int ii = planElements.size() - 1; ii > index; ii--) {
			if (planElements.get(ii) instanceof Activity) {
				Activity act = (Activity) planElements.get(ii);
				if (!StageActivityTypeIdentifier.isStageActivity(act.getType()) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ||
						act.getType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION)) {
					theIndex = ii;
				}
			}
		}
		return theIndex;
	}

	protected Boolean isHomeChargingTrip(MobsimAgent mobsimAgent, Plan modifiablePlan, List<Leg> evLegs, ElectricVehicle ev) {

		int firstEvLegIndex = modifiablePlan.getPlanElements().indexOf(evLegs.get(0));
		Id<Link> homeLink = EditPlans.findRealActBefore(mobsimAgent,firstEvLegIndex).getLinkId();
		Id<Person> person =  mobsimAgent.getId();
		boolean isHomeTrip = EditPlans.findRealActAfter(mobsimAgent,modifiablePlan.getPlanElements().indexOf(evLegs.get(evLegs.size()-1))).getLinkId().equals(homeLink);
		boolean hasHomeCharger = chargingInfrastructureSpecification.getChargerSpecifications().values().stream()
				.filter(chargerSpecification -> ev.getChargerTypes().contains(chargerSpecification.getChargerType()))
				.filter(chargerSpecification->this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().isEmpty())// Delete this line to go back to the original
				.map(chargerSpecification -> chargerSpecification.getLinkId())
				.anyMatch(linkId -> linkId.equals(homeLink));
		
//		for(ChargerSpecification c:chargingInfrastructureSpecification.getChargerSpecifications().values()){
//			if(ev.getChargerTypes().contains(c.getChargerType())&& 
//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
//							.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
//							.getPersonsAccecibleTo().isEmpty()) && c.getLinkId().equals(homeLink)){
//			return isHomeTrip;
//		}
//		}
		  
		
		return isHomeTrip && hasHomeCharger;
	//	return false;
	}
	
	protected boolean hasHomeCharger(MobsimAgent mobsimAgent, Plan modifiablePlan, List<Leg> evLegs, ElectricVehicle ev){
		
		int firstEvLegIndex = modifiablePlan.getPlanElements().indexOf(evLegs.get(0));
		Id<Link> homeLink = EditPlans.findRealActBefore(mobsimAgent,firstEvLegIndex).getLinkId();
		Id<Person> person =  mobsimAgent.getId();
		boolean hasHomeCharger = chargingInfrastructureSpecification.getChargerSpecifications().values().stream()
				.filter(chargerSpecification -> ev.getChargerTypes().contains(chargerSpecification.getChargerType()))
				.filter(chargerSpecification->this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(chargerSpecification.getId())
						.getPersonsAccecibleTo().isEmpty())// Delete this line to go back to the original
				.map(chargerSpecification -> chargerSpecification.getLinkId())
				.anyMatch(linkId -> linkId.equals(homeLink));
		
//		for(ChargerSpecification c:chargingInfrastructureSpecification.getChargerSpecifications().values()){
//			if(ev.getChargerTypes().contains(c.getChargerType())&& 
//					(this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
//							.getPersonsAccecibleTo().contains(person) ||this.chargerPricingProfiles.getChargerPricingProfiles().get(c.getId())
//							.getPersonsAccecibleTo().isEmpty()) && c.getLinkId().equals(homeLink)){
//			return true;
//		}
//		}
		return hasHomeCharger;
		//return false;
	}
	/**
	 * 
	 * @param plan 
	 * @param routingMode EV leg mode
	 * @param electricVehicleSpecification
	 * @param actWhileCharging the activity both before and after the plugin
	 * @param chargingLink
	 * @param tripRouter
	 */
	protected void planPluginTripFromHomeToCharger(Plan plan, String routingMode, ElectricVehicleSpecification electricVehicleSpecification,Activity actWhileCharging, Link chargingLink, TripRouter tripRouter) {
		PopulationFactory factory = scenario.getPopulation().getFactory();
		Facility fromFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());
		Facility chargerFacility = new LinkWrapperFacility(chargingLink);
		Facility toFacility = FacilitiesUtils.toFacility(actWhileCharging, scenario.getActivityFacilities());

		//copy the First Act and set the end time to 0s. Since every plan has to start with an act
		Activity newFirstAct = factory.createActivityFromLinkId(actWhileCharging.getType(), actWhileCharging.getLinkId());
		newFirstAct.setEndTime(0);
		double now = 0;
		now = time.decideOnElementEndTime(newFirstAct, now).seconds();
		plan.getPlanElements().add(0, newFirstAct);

		
		//add leg to charger
		List<? extends PlanElement> routeToCharger = tripRouter.calcRoute(TransportMode.car, fromFacility, chargerFacility, now, plan.getPerson(),null);// Should be routing mode
		//set the vehicle id
		for (Leg leg : TripStructureUtils.getLegs(routeToCharger)) {
			if(leg.getMode().equals(routingMode)){
				NetworkRoute route = ((NetworkRoute) leg.getRoute());
				if(route.getVehicleId() == null) route.setVehicleId(Id.createVehicleId(electricVehicleSpecification.getId()));
			}
		}


		//add plugin act
		Activity pluginAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(chargingLink.getCoord(),
				chargingLink.getId(), routingMode + UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER);//This is how to add a aplugin activity
		plan.getPlanElements().add(2, pluginAct);
		TripRouter.insertTrip(plan, newFirstAct,routeToCharger,pluginAct);
		//add walk leg to destination
		List<? extends PlanElement> routeFromChargerToAct = tripRouter.calcRoute(TransportMode.walk, chargerFacility, toFacility,
				time.decideOnActivityEndTimeAlongPlan(newFirstAct, plan).seconds(), plan.getPerson(),null);
		int indexRouteFromChargerToAct = plan.getPlanElements().indexOf(pluginAct) + 1;
		plan.getPlanElements().add(indexRouteFromChargerToAct, routeFromChargerToAct.get(0));
		plan.getPlanElements().add(indexRouteFromChargerToAct+1, actWhileCharging);

	}


	class PersonContainer2{
	private final Id<Person> personId;
	private final String reason;


	PersonContainer2 (Id<Person> personId, String reason){
		this.personId = personId;
		this.reason = reason;

	}


}
}



