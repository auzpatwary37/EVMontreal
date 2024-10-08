package locationChoice;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math.linear.RealVector;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerReader;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerWriter;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.contrib.ev.infrastructure.ImmutableChargerSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import EVPricing.ChargerPricingProfile;
import EVPricing.ChargerPricingProfileReader;
import EVPricing.ChargerPricingProfileWriter;
import EVPricing.ChargerPricingProfiles;

public class FirstStepAnalysis {
public static void main(String[] args) {
	String PopulationFileLocation = "data\\10p\\plan.xml";
	String FacilityFileLocation = "data\\10p\\montreal_facilities.xml.gz";
	String VehicleFileLocation = "data\\10p\\vehicle.xml";
	String NetworkFileLocation = "data\\10p\\montreal_network.xml";
	String chargerSpecificationFile = "data\\10p\\charger.xml";
	
	String usedFacilityCoordsLocation = "data\\10p\\facilityUsed.csv";
	String chargerCoordsLocation = "data\\10p\\chargerCoords.csv";
	String chargerCoordsLocationNew = "data\\10p\\chargerCoordsNew_noDuration.csv";
	String featureFileLocation = "data\\10p\\features_noDuration.csv";
	String oldPricingProfileFile = "data\\10p\\pricingProfiles.xml";
	String newPricingProfileFile = "data\\10p\\pricingProfiles_new_noDuration.xml";
	String newChargerFile = "data\\10p\\charger_new_noDuration.xml";
	Config config = ConfigUtils.createConfig();
	ConfigUtils.loadConfig(config,"data\\10p\\config.xml");
	config.plans().setInputFile(PopulationFileLocation);
	config.vehicles().setVehiclesFile(VehicleFileLocation);
	config.network().setInputFile(NetworkFileLocation);
	config.facilities().setInputFile(FacilityFileLocation);
	config.transit().setTransitScheduleFile("data\\10p\\montreal_transit_schedules.xml");
	config.transit().setVehiclesFile("data\\10p\\montreal_transit_vehicles.xml");
	
	
	Scenario scenario  = ScenarioUtils.loadScenario(config);
	Population population = scenario.getPopulation();
	Network network = scenario.getNetwork();
	ActivityFacilities facilities = scenario.getActivityFacilities();
	Vehicles vehicles = scenario.getVehicles();
	ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();
	ChargerPricingProfiles pricingProfiles = new ChargerPricingProfileReader().readChargerPricingProfiles(oldPricingProfileFile);
	new ChargerReader(csp).readFile(chargerSpecificationFile);
	Set<Id<Vehicle>>evIds = new HashSet<>();
	Set<Id<ActivityFacility>> usedFacilities = new HashSet<>();
	vehicles.getVehicles().values().forEach(v->{
		if(VehicleUtils.getHbefaTechnology(v.getType().getEngineInformation()).equals("electricity")){
			evIds.add(v.getId());
		}
	});
	Set<String> activityType = new HashSet<>();
	population.getPersons().values().forEach(p->{
		TripStructureUtils.getActivities(p.getSelectedPlan().getPlanElements(), StageActivityHandling.ExcludeStageActivities).stream().forEach(a->{
			usedFacilities.add(a.getFacilityId());
			activityType.add(a.getType());
		});
	});
	System.out.println("total links = "+network.getLinks().size());
	System.out.println("total chargers = "+csp.getChargerSpecifications().size());
	System.out.println("total population = "+population.getPersons().size());
	System.out.println("total Ev = "+evIds.size()+", "+(double)evIds.size()/population.getPersons().size()*100 +"% of total population.");
	System.out.println("total facilities = "+ facilities.getFacilities().size());
	System.out.println("total facilities used = "+ usedFacilities.size() + ", "+(double)usedFacilities.size()/facilities.getFacilities().size()*100+"% of total facilities.");
	System.out.println("activity types are: "+activityType);
	Map<String,Tuple<Double,Double>> activityDurations = new HashMap<>();
	activityDurations.put("other", new Tuple<>(1.5 * 3600, 0.5 * 3600));
    activityDurations.put("education", new Tuple<>(6.0 * 3600, 1.0 * 3600));
    activityDurations.put("shop", new Tuple<>(1.0 * 3600, 0.2 * 3600));
    activityDurations.put("work", new Tuple<>(8.0 * 3600, 3.0 * 3600));
    activityDurations.put("leisure", new Tuple<>(2.5 * 3600, 0.5 * 3600));
    activityDurations.put("home", new Tuple<>(12.0 * 3600, 6.0 * 3600));
	
	for(String at:activityType) {
		ActivityParams param;
		if(config.planCalcScore().getActivityParams(at)==null) {
			param = new ActivityParams();
			config.planCalcScore().addActivityParams(param);
			param.setActivityType(at);
		}else {
			param = config.planCalcScore().getActivityParams(at);
		}
		if(param.getTypicalDuration().isUndefined())param.setTypicalDuration(activityDurations.get(at).getFirst());
		if(param.getMinimalDuration().isUndefined())param.setMinimalDuration(activityDurations.get(at).getSecond());
	}
	//create the feature vectors. First create maps. 
	Map<Id<ActivityFacility>,Tuple<Double,Double>> evUserFacilityUsage = new HashMap<>();
	Map<Id<ActivityFacility>,Tuple<Double,Double>> evNonUserFacilityUsage = new HashMap<>();
	
	population.getPersons().values().forEach(p->{
		boolean evUser = false;
		Id<Vehicle> vehicleId = Id.createVehicleId(p.getId().toString());
		if(evIds.contains(vehicleId)) {
			evUser = true;
		}
		boolean atAll = evUser;
		TripStructureUtils.getActivities(p.getSelectedPlan().getPlanElements(),StageActivityHandling.ExcludeStageActivities).stream().forEach(a->{
			double duration = config.planCalcScore().getActivityParams(a.getType()).getTypicalDuration().seconds();
			if(a.getStartTime().isDefined() && a.getEndTime().isDefined()) {
				duration =a.getEndTime().seconds()-a.getStartTime().seconds();
			}else if(a.getStartTime().isUndefined() && a.getEndTime().isDefined()) {
				duration = a.getEndTime().seconds();
			}else if(a.getStartTime().isDefined() && a.getEndTime().isUndefined()) {
				duration = 24*3600.-a.getStartTime().seconds();
			}
			double d = duration;
			if(atAll) {
				evUserFacilityUsage.compute(a.getFacilityId(), (k,v)->v==null?new Tuple<>(1.,d):new Tuple<>(v.getFirst()+1,v.getSecond()+d));
			}else {
				evNonUserFacilityUsage.compute(a.getFacilityId(), (k,v)->v==null?new Tuple<>(1.,d):new Tuple<>(v.getFirst()+1,v.getSecond()+d));
			}
		});
		
	});
	MapToArray<String> featuresMapToArray =null;
	Map<Id<ActivityFacility>,RealVector> features = new HashMap<>();
	Map<Id<Link>,Id<ActivityFacility>> linkToFacilityMap = new HashMap<>();
	
	for(Id<ActivityFacility>facId:usedFacilities){
		Id<Link> linkId = facilities.getFacilities().get(facId).getLinkId();
		double evUser = 0;
		double nonEvUser = 0;
		double evUserDuration = 0;
		double nonEvUserDuration = 0;
		if(linkToFacilityMap.containsKey(linkId)) {
			Map<String,Double>oldFeature = featuresMapToArray.getMap(features.get(linkToFacilityMap.get(linkId)).toArray());
			evUser = oldFeature.get(Hotspot.activityNumberString+"_"+Hotspot.EvUserString);
			nonEvUser = oldFeature.get(Hotspot.activityNumberString+"_"+Hotspot.nonEvUserString);
			//evUserDuration = oldFeature.get(Hotspot.acitivityDurationString+"_"+Hotspot.EvUserString);
			//nonEvUserDuration = oldFeature.get(Hotspot.acitivityDurationString+"_"+Hotspot.nonEvUserString);
		}else {
			linkToFacilityMap.put(linkId, facId);
		}
		
		Map<String,Double> featureMap = new HashMap<>();
		
		
		
		if(evUserFacilityUsage.get(facId)!=null) {
			
			evUserDuration = (evUserDuration*evUser+ evUserFacilityUsage.get(facId).getSecond())/(evUser+evUserFacilityUsage.get(facId).getFirst());
			evUser = evUser + evUserFacilityUsage.get(facId).getFirst();
		}
		if(evNonUserFacilityUsage.get(facId)!=null) {
			
			nonEvUserDuration = (nonEvUser*nonEvUserDuration+evNonUserFacilityUsage.get(facId).getSecond())/(nonEvUser+evNonUserFacilityUsage.get(facId).getFirst());
			nonEvUser = nonEvUser+evNonUserFacilityUsage.get(facId).getFirst();
		}
		featureMap.put(Hotspot.locationX, facilities.getFacilities().get(facId).getCoord().getX());
		featureMap.put(Hotspot.locationY, facilities.getFacilities().get(facId).getCoord().getY());
		//featureMap.put(Hotspot.acitivityDurationString+"_"+Hotspot.EvUserString, evUserDuration);
		//featureMap.put(Hotspot.acitivityDurationString+"_"+Hotspot.nonEvUserString, nonEvUserDuration);
		featureMap.put(Hotspot.activityNumberString+"_"+Hotspot.EvUserString, evUser);
		featureMap.put(Hotspot.activityNumberString+"_"+Hotspot.nonEvUserString, nonEvUser);
		if(featuresMapToArray==null) {
			featuresMapToArray = new MapToArray<String>("features",featureMap.keySet());
		}
		features.put(facId, featuresMapToArray.getRealVector(featureMap));
	}
	List<Hotspot> hotspots = new ArrayList<>();
	for(Entry<Id<Charger>, ChargerSpecification> c:csp.getChargerSpecifications().entrySet()) {
		Hotspot hotspot = new Hotspot(c.getKey().toString(), featuresMapToArray);
		Id<ActivityFacility> facId = linkToFacilityMap.get(c.getValue().getLinkId());
		if(facId!=null) {
			hotspot.addFacility(facId,features.get(facId));
			hotspot.setCentroidFacilityId(facId,features.get(facId));
		}else {
			ActivityFacility facility = facilities.getFactory().createActivityFacility(Id.create(c.getKey().toString(),ActivityFacility.class),c.getValue().getLinkId());
			Coord coord = FacilitiesUtils.decideOnCoord(facility, network, config);
			facility.setCoord(coord);
			facilities.addActivityFacility(facility);
			facId = facility.getId();
			double evUser = 0;
			double nonEvUser = 0;
			double evUserDuration = 0;
			double nonEvUserDuration = 0;
			Map<String,Double> featureMap = new HashMap<>();
			featureMap.put(Hotspot.locationX, facilities.getFacilities().get(facId).getCoord().getX());
			featureMap.put(Hotspot.locationY, facilities.getFacilities().get(facId).getCoord().getY());
			//featureMap.put(Hotspot.acitivityDurationString+"_"+Hotspot.EvUserString, evUserDuration);
			//featureMap.put(Hotspot.acitivityDurationString+"_"+Hotspot.nonEvUserString, nonEvUserDuration);
			featureMap.put(Hotspot.activityNumberString+"_"+Hotspot.EvUserString, evUser);
			featureMap.put(Hotspot.activityNumberString+"_"+Hotspot.nonEvUserString, nonEvUser);
			features.put(facId, featuresMapToArray.getRealVector(featureMap));
			hotspot.addFacility(facId,features.get(facId));
			hotspot.setCentroidFacilityId(facId,features.get(facId));
		}
		hotspots.add(hotspot);
	}
	System.out.println("Statistics till now");
	System.out.println("Total hotspot created = "+ hotspots.size());
	System.out.println("Total facilities with feature = "+features.size());
	
	try {
		FileWriter fwFacility = new FileWriter(new File(usedFacilityCoordsLocation));
		fwFacility.append("facilityId,X,Y\n");
		for(Id<ActivityFacility> facId:usedFacilities) {
			Coord coord = facilities.getFacilities().get(facId).getCoord();
			if(coord==null)coord = FacilitiesUtils.decideOnCoord(facilities.getFacilities().get(facId), network, config);
			fwFacility.append(facId+","+coord.getX()+","+coord.getY()+"\n");
			fwFacility.flush();
		}
		fwFacility.close();
		FileWriter fwCharger = new FileWriter(new File(chargerCoordsLocation));
		fwCharger.append("chargerId,X,Y\n");
		for(Hotspot h:hotspots) {
			Coord coord = facilities.getFacilities().get(h.getCentroidFacility()).getCoord();
			fwCharger.append(h.getHotspotId().toString()+","+coord.getX()+","+coord.getY()+"\n");
			fwCharger.flush();
		}
		fwCharger.close();
		FileWriter fwFacilityFeature = new FileWriter(new File(featureFileLocation));
		fwFacilityFeature.append("facilityId,X,Y,evUser,evUserDuration,nonEvUser,nonEvUserDuration\n");
		for(Id<ActivityFacility> facId:features.keySet()) {
			Map<String,Double> f = featuresMapToArray.getMap(features.get(facId).toArray());
			fwFacilityFeature.append(facId+","+f.get(Hotspot.locationX)+","+f.get(Hotspot.locationY)+","+f.get(Hotspot.activityNumberString+"_"+Hotspot.EvUserString)
			+","+f.get(Hotspot.acitivityDurationString+"_"+Hotspot.EvUserString)+","+f.get(Hotspot.activityNumberString+"_"+Hotspot.nonEvUserString)+
			","+f.get(Hotspot.acitivityDurationString+"_"+Hotspot.nonEvUserString)+"\n");
			fwFacilityFeature.flush();
		}
		fwFacilityFeature.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	//starting k-means
	
	KMeansClusterer clusterer = new KMeansClusterer(hotspots, 2500);
	clusterer.runKMeans(1000, features);
	hotspots = clusterer.getHotspots();
	
	try {
		FileWriter fwCharger = new FileWriter(new File(chargerCoordsLocationNew));
		fwCharger.append("chargerId,X,Y,plugCount,power\n");
		for(Hotspot h:hotspots) {
			Coord coord = facilities.getFacilities().get(h.getCentroidFacility()).getCoord();
			int plugCount = csp.getChargerSpecifications().get(Id.create(h.getHotspotId().toString(), Charger.class)).getPlugCount();
			double power = csp.getChargerSpecifications().get(Id.create(h.getHotspotId().toString(), Charger.class)).getPlugPower();
			fwCharger.append(h.getHotspotId().toString()+","+coord.getX()+","+coord.getY()+","+plugCount+","+power+"\n");
			fwCharger.flush();
		}
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
	//create charger and pricing profiles
	Map<String,double[]> PeakPricing = new HashMap<>(); 
	double[] nonLinear = null;
	//Fast peak hour
	nonLinear = new double[3];
	nonLinear[0] = 10.00;
	nonLinear[1] = 10.00;
	nonLinear[2] = 10.00;
	PeakPricing.put("Fast", nonLinear);
	int[] peakTime = new int[] {};
	int[] offPeakTime = new int[] {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
	Map<String,double[]> offPeakPricing = new HashMap<>(); 
	//Fast off peak hour
	nonLinear = new double[3];
	nonLinear[0] = 4.0;
	nonLinear[1] = 4.0;
	nonLinear[2] = 4.0;
	
	offPeakPricing.put("Fast", nonLinear);
	
	hotspots.forEach(h->{
		if(!csp.getChargerSpecifications().containsKey(Id.create(h.getHotspotId().toString(), Charger.class))) {
			ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
					.id(Id.create(h.getHotspotId().toString(), Charger.class))
					.linkId(facilities.getFacilities().get(h.getCentroidFacility()).getLinkId())
					.chargerType("Fast")
					.plugCount(10)
					.plugPower(1000 * 50)
					.build();

			csp.addChargerSpecification(c);
			ChargerPricingProfile pp = new ChargerPricingProfile(c.getId(), "zone1", 30);
			for(int i: peakTime) {
				double[] pprofile = PeakPricing.get(c.getChargerType()).clone();
				pp.addHourlyPricingProfile(i,applyMultiplier(pprofile,1));
				pp.addHourlyPricingProfilePerHr(i, applyMultiplier(pprofile,5));

			}
			for(int i: offPeakTime) {

				pp.addHourlyPricingProfile(i, applyMultiplier(offPeakPricing.get(c.getChargerType()),1));
				pp.addHourlyPricingProfilePerHr(i, applyMultiplier(offPeakPricing.get(c.getChargerType()),5));
			}
			pricingProfiles.addChargerPricingProfile(pp);
		}

	});
	
	new ChargerWriter(csp.getChargerSpecifications().values().stream()).write(newChargerFile);
	new ChargerPricingProfileWriter(pricingProfiles).write(newPricingProfileFile);
}

public static double[] applyMultiplier(double[] o,double m) {
	double[] D = new double[o.length];
	for(int i = 0; i<o.length;i++) {
		D[i] = o[i]*m;
	}
	return D;
}
}
