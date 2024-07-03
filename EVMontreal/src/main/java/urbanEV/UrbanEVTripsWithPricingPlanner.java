package urbanEV;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class UrbanEVTripsWithPricingPlanner extends UrbanEVTripsPlanner{
	
	@Inject
	private TimeInterpretation time;
	
	private int numberOfPlansToSavePerPerson = 4;
	public static final double cov = 1.2;
	public static final String ifBrokenActivity = "ifBroken";
	public static final String savedEVPlansKey = "urbanEVPlan";// we will save the modified plans->score to the agents' memory along with its utility 
	@Override
	protected void processPlans(Map<Plan, Set<Id<Vehicle>>> selectedEVPlans) {
		super.processPlans(selectedEVPlans);
		
	}
	public int generateRandom(int start, int end, List<Integer> excludeRows) {
	    
	    int range = end - start + 1;
	    Random rand = MatsimRandom.getRandom();
	    int random = rand.nextInt(range) + 1;
	    while(excludeRows.contains(random)) {
	        random = rand.nextInt(range) + 1;
	    }

	    return random;
	}
	@Override
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
		List<Activity> actWhileChargingList;
		ChargerSpecification selectedCharger;
		int legIndexCounter = legIndex;
		//find suitable non-stage activity before SOC threshold passover
		actWhileChargingList = activityWhileChargingFinder.findActivitiesWhileChargingBeforeLeg(mobsimagent, modifiablePlan, (Leg) modifiablePlan.getPlanElements().get(legIndexCounter));
		List<Integer> allreadyChecked = new ArrayList<>();
		actWhileChargingList = actWhileChargingList.stream().filter(a->a.getAttributes().getAttribute(ifBrokenActivity)!=null).collect(Collectors.toList());
		do {

			if (actWhileChargingList == null){
				log.warn(mobsimagent + " can't find a suitable activity prior the critical leg!");
				PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "can't find a suitable activity prior the critical leg!");
				personContainer2s.add(personContainer2);
				return;
			}
			int ind = generateRandom(0,actWhileChargingList.size(),allreadyChecked);
			allreadyChecked.add(ind);
			selectedCharger = selectChargerNearToLink(mobsimagent.getId(),actWhileChargingList.get(ind).getLinkId(), electricVehicleSpecification, modeNetwork);
			
			if(selectedCharger == null){

				leg = evLegs.get(evLegs.indexOf(leg)-1);
				legIndexCounter = modifiablePlan.getPlanElements().indexOf(leg);
			}
			actWhileCharging = actWhileChargingList.get(ind);
		} while (actWhileCharging != null && selectedCharger == null && allreadyChecked.size()!=actWhileChargingList.size());
		if (selectedCharger == null){
			log.warn(mobsimagent + " can't find a suitable activity prior the critical leg which have a charger!");
			PersonContainer2 personContainer2 = new PersonContainer2(mobsimagent.getId(), "can't find a suitable activity prior the critical leg!");
			personContainer2s.add(personContainer2);
			return;
		}

		//		Preconditions.checkNotNull(actWhileCharging, "could not insert plugin activity in plan of agent " + mobsimagent.getId() +
		//				".\n One reason could be that the agent has no suitable activity prior to the leg for which the " +
		//				" energy threshold is expected to be exceeded. \n" +
		//				" Another reason  might be that it's vehicle is running beyond energy threshold during the first leg of the day." +
		//				"That could possibly be avoided by using EVNetworkRoutingModule..."); //TODO let the sim just run and let the ev run empty!?

		//TODO what if actWhileCharging does not hold a link id?

		Link chargingLink = modeNetwork.getLinks().get(selectedCharger.getLinkId());

		boolean breakAct = MatsimRandom.getRandom().nextBoolean();
		boolean chargeAtStart = false;
		if(breakAct == true) {
			chargeAtStart = MatsimRandom.getRandom().nextBoolean();

			// now we choose duration of the charging and place to break the activity
			ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(electricVehicleSpecification, driveConsumptionFactory, auxConsumptionFactory, chargingPowerFactory);
			pseudoVehicle.getBattery().setCharge(pseudoVehicle.getBattery().getCapacity());
			//double charge = pseudoVehicle.getChargingPower().calcChargingPower(selectedCharger) * chargingDuration;
			double reqCharge = 0;
			for(int i = modifiablePlan.getPlanElements().indexOf(leg);i<modifiablePlan.getPlanElements().size();i++) {
				if(modifiablePlan.getPlanElements().get(i) instanceof Leg) {
					Leg leg_ = (Leg) modifiablePlan.getPlanElements().get(i);
					if (leg_.getMode().equals(leg.getMode()) && VehicleUtils.getVehicleId(modifiablePlan.getPerson(), leg.getMode()).toString().equals(pseudoVehicle.getId().toString())) {
						emulateVehicleDischarging(pseudoVehicle, leg);
					}
				}
			}
			reqCharge = (pseudoVehicle.getBattery().getCapacity() - pseudoVehicle.getBattery().getSoc())*1.2;
			if(reqCharge<0)reqCharge = pseudoVehicle.getBattery().getCapacity();
			double chargeTime = reqCharge/pseudoVehicle.getChargingPower().calcChargingPower(selectedCharger);
			double randomChargeTime = chargeTime + MatsimRandom.getRandom().nextGaussian()*chargeTime*cov;//
			double actBreakTime = 0;
			TripRouter tripRouter = tripRouterProvider.get();
			int ind = modifiablePlan.getPlanElements().indexOf(actWhileCharging);
			if(chargeAtStart) {
				Activity pluginTripOrigin = findRealOrChargingActBefore(mobsimagent, modifiablePlan.getPlanElements().indexOf(actWhileCharging));
				this.planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, pluginTripOrigin, actWhileCharging, chargingLink, tripRouter);
				double beforeActStartTime = actWhileCharging.getStartTime().seconds();
				double beforeActEndTime = beforeActStartTime+randomChargeTime; 
				Activity actBeforeBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actBeforeBreak.setEndTime(beforeActEndTime);
				actBeforeBreak.getAttributes().putAttribute(ifBrokenActivity, true);
				Activity actAfterBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actAfterBreak.setEndTime(actWhileCharging.getEndTime().seconds());
				actAfterBreak.getAttributes().putAttribute(ifBrokenActivity, true);
				Leg legDummy = scenario.getPopulation().getFactory().createLeg(TransportMode.walk);
				modifiablePlan.getPlanElements().add(ind,actBeforeBreak);
				modifiablePlan.getPlanElements().add(ind+1,legDummy);
				modifiablePlan.getPlanElements().add(ind+2,actAfterBreak);
				modifiablePlan.getPlanElements().remove(actWhileCharging);
				this.planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBeforeBreak, actAfterBreak, chargingLink, tripRouter, beforeActEndTime);
			}else {
				actBreakTime = actWhileCharging.getEndTime().seconds()-randomChargeTime-600;
				Activity actBeforeBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actBeforeBreak.setEndTime(actBreakTime);
				actBeforeBreak.getAttributes().putAttribute(ifBrokenActivity, true);
				Activity actAfterBreak = scenario.getPopulation().getFactory().createActivityFromCoord(actWhileCharging.getType(), actWhileCharging.getCoord());
				actAfterBreak.setEndTime(actWhileCharging.getEndTime().seconds());
				actAfterBreak.getAttributes().putAttribute(ifBrokenActivity, true);
				Leg legDummy = scenario.getPopulation().getFactory().createLeg(routingMode);
				modifiablePlan.getPlanElements().add(ind,actBeforeBreak);
				modifiablePlan.getPlanElements().add(ind+1,legDummy);
				modifiablePlan.getPlanElements().add(ind+2,actAfterBreak);
				modifiablePlan.getPlanElements().remove(actWhileCharging);
				this.planPluginTrip(modifiablePlan, routingMode, electricVehicleSpecification, actBeforeBreak, actAfterBreak, chargingLink, tripRouter);
				Leg plugoutLeg = activityWhileChargingFinder.getNextLegOfRoutingModeAfterActivity(ImmutableList.copyOf(modifiablePlan.getPlanElements()), actWhileCharging, routingMode);
				Activity plugoutTripDestination = findRealOrChargingActAfter(mobsimagent, modifiablePlan.getPlanElements().indexOf(plugoutLeg));
				planPlugoutTrip(modifiablePlan, routingMode, electricVehicleSpecification, actAfterBreak, plugoutTripDestination, chargingLink, tripRouter, time.decideOnActivityEndTimeAlongPlan(actBeforeBreak, modifiablePlan).seconds());
			}
			
			
		}else {
			
			

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
		}
		if(!isConsistant(modifiablePlan)) {
			System.out.println("Plan is not consistant!!! Debug!!!");
		}

	}
	/**
	 * returns leg for which the critical soc is exceeded or the last of all ev legs.
	 *
	 * @param modifiablePlan
	 * @param pseudoVehicle
	 * @param originalVehicleId
	 * @return
	 */
	@Override
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
					
					
					pseudoVehicle.getBattery().setCharge(pseudoVehicle.getBattery().getCharge()+pseudoVehicle.getChargingPower().calcChargingPower(chargerSpecification) * chargingDuration);
				}
			} else throw new IllegalArgumentException();
		}

		return lastLegWithVehicle;
	}

}
