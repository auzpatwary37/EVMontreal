package EVPricing;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.core.network.NetworkUtils;



public class ChargerPricingProfiles {
	
	private final Map<String,Coord> zone;
	private Map<Id<Charger>,ChargerPricingProfile> chargerPricingProfiles = new HashMap<>();
	private Network zoneNet;
	
	public ChargerPricingProfiles(Map<String,Coord> zones) {
		this.zone = zones;
		Network zoneNet = NetworkUtils.createNetwork();
		this.zone.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		this.zoneNet = zoneNet;
	}
	
	public void addChargerPricingProfile(ChargerPricingProfile profile) {
		
		this.chargerPricingProfiles.put(profile.getChargerId(), profile);
	}
	
	public String getZoneId(Coord coord) {
		return NetworkUtils.getNearestNode(zoneNet,coord).getId().toString();
	}
	
	/**
	 * Use this to create a flat priced charger
	 * @param zones
	 * @param chargerSet
	 * @param price
	 */
	public ChargerPricingProfiles(Map<String,Coord> zones, Map<Id<Charger>,Charger> chargerSet, double price, double profileTimeStepInMin) {
		this.zone = zones;
		Network zoneNet = NetworkUtils.createNetwork();
		this.zoneNet = zoneNet;
		this.zone.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		chargerSet.entrySet().stream().forEach(cId->{
			String zoneId = NetworkUtils.getNearestNode(zoneNet,cId.getValue().getCoord()).getId().toString();
			new ChargerPricingProfile(cId.getKey(),zoneId, price, profileTimeStepInMin);
		});
	}
	
	
	
	/**
	 * Use this to generate a zero priced Charger
	 * @param zones
	 * @param chargerSet
	 */
	public ChargerPricingProfiles(Map<String,Coord> zones, Map<Id<Charger>,Charger> chargerSet) {
		this.zone = zones;
		Network zoneNet = NetworkUtils.createNetwork();
		this.zoneNet = zoneNet;
		this.zone.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		chargerSet.entrySet().stream().forEach(cId->{
			String zoneId = NetworkUtils.getNearestNode(zoneNet,cId.getValue().getCoord()).getId().toString();
			new ChargerPricingProfile(cId.getKey(),zoneId,60);
		});
	}
	/**
	 * Use this to generate a flatNonLienar priced charger
	 * @param zones
	 * @param chargerSet
	 * @param faltNonLinear
	 */
	public ChargerPricingProfiles(Map<String,Coord> zones, Map<Id<Charger>,Charger> chargerSet, double[] flatNonLinear, double profileTimeStepInMin) {
		this.zone = zones;
		Network zoneNet = NetworkUtils.createNetwork();
		
		this.zone.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		this.zoneNet = zoneNet;
		chargerSet.entrySet().stream().forEach(cId->{
			String zoneId = NetworkUtils.getNearestNode(zoneNet,cId.getValue().getCoord()).getId().toString();
			new ChargerPricingProfile(cId.getKey(),zoneId, flatNonLinear, profileTimeStepInMin);
		});
	}


	
	
	/**
	 * Use this to generate a flatNonLienarDynamicZonal priced charger
	 * @param zones
	 * @param chargerSet
	 * @param faltNonLinear
	 */
	public ChargerPricingProfiles(Map<String,Coord> zones, Map<Id<Charger>,Charger> chargerSet, Map<String,Map<Integer, double[]>> zonalflatNonLinearDynamic,double profileTimeStepInMin) {
		this.zone = zones;
		Network zoneNet = NetworkUtils.createNetwork();
		this.zone.entrySet().stream().forEach(z->{
			NetworkUtils.createAndAddNode(zoneNet, Id.createNodeId(z.getKey()), z.getValue());
		});
		chargerSet.entrySet().stream().forEach(cId->{
			String zoneId = NetworkUtils.getNearestNode(zoneNet,cId.getValue().getCoord()).getId().toString();
			new ChargerPricingProfile(cId.getKey(),zoneId, zonalflatNonLinearDynamic.get(zoneId),profileTimeStepInMin);
		});
	}
	
	

	public Map<Id<Charger>, ChargerPricingProfile> getChargerPricingProfiles() {
		return chargerPricingProfiles;
	}

	public void setChargerPricingProfiles(Map<Id<Charger>, ChargerPricingProfile> chargerPricingProfiles) {
		this.chargerPricingProfiles = chargerPricingProfiles;
	}

	public Map<String, Coord> getZone() {
		return zone;
	}
	
	
}
