package urbanEV;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargerWriter;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecificationImpl;
import org.matsim.contrib.ev.infrastructure.ImmutableChargerSpecification;
import org.matsim.core.network.NetworkUtils;

import EVPricing.ChargerPricingProfile;
import EVPricing.ChargerPricingProfileWriter;
import EVPricing.ChargerPricingProfiles;

public class RandomCharger {
	public static void main(String[] args) {
		Network net = NetworkUtils.readNetwork("Montreal_Scenario_New\\Siouxfalls_network_PT.xml");
		Map<Id<Link>, ? extends Link>linkSet = new HashMap<>(net.getLinks());
		linkSet.entrySet().stream().forEach(l->{
			if(!l.getValue().getAllowedModes().contains("car") && !l.getValue().getAllowedModes().contains("car_passenger"))net.removeLink(l.getKey());
		});
		Map<Id<Node>, ? extends Node>nodeSet = new HashMap<>(net.getNodes());
		nodeSet.entrySet().stream().forEach(n->{
			if(n.getValue().getInLinks().size()==0 && n.getValue().getOutLinks().size()==0)net.removeNode(n.getKey());
		});
		ChargingInfrastructureSpecification csp = new ChargingInfrastructureSpecificationImpl();
		for(Id<Link> lid:net.getLinks().keySet()) {
			double random = Math.random();
			if(random < 0.5) {
				ChargerSpecification c = ImmutableChargerSpecification.newBuilder()
						.id(Id.create(lid.toString(), Charger.class))
						.linkId(lid)
						.chargerType("Level 1")
						.plugCount(5)
						.plugPower(10000)
						.build();
				
				csp.addChargerSpecification(c);
				
			}
			
		}
		new ChargerWriter(csp.getChargerSpecifications().values().stream()).write("randomlocation.xml");
		
		double pricingSchemeTimeSlotinMin = 30;
		
		Coord coord = new Coord(683275.,4823785.);
		Map<String,Coord> zones = new HashMap<>();
		
		zones.put("zone1", coord);
		
		Map<String,double[]> offPeakPricing = new HashMap<>(); 
		
		double[] nonLinear = null;
		
		//Level 1 off peak hour
		nonLinear = new double[3];
		nonLinear[0] = .11;
		nonLinear[1] = .22;
		nonLinear[2] = .30;
		
		offPeakPricing.put("Level 1", nonLinear);
		
		Map<String,double[]> PeakPricing = new HashMap<>(); 
		
		
		
		//Level 1 peak hour
		nonLinear = new double[3];
		nonLinear[0] = .15;
		nonLinear[1] = .25;
		nonLinear[2] = .35;
		
		PeakPricing.put("Level 1", nonLinear);
		int[] peakTime = new int[] {7,8,9,10,11,12,13,14,15,16,17,18,19,20};
		int[] offPeakTime = new int[] {0,1,2,3,4,5,6,21,22,23};
		
		ChargerPricingProfiles cpp = new ChargerPricingProfiles(zones);
		for (Id<Charger> cid: csp.getChargerSpecifications().keySet()) {
			ChargerPricingProfile pp = new ChargerPricingProfile(cid, "zone1", pricingSchemeTimeSlotinMin);
			for(int i: peakTime) {
				pp.addHourlyPricingProfile(i, PeakPricing.get("Level 1"));
			}
			for(int i: offPeakTime) {
				pp.addHourlyPricingProfile(i, offPeakPricing.get("Level 1"));
			}
			cpp.addChargerPricingProfile(pp);
		}
		new ChargerPricingProfileWriter(cpp).write("Montreal_Scenario_New\\SFpricingprofile.xml");
	}

}
