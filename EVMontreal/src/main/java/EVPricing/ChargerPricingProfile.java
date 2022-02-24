package EVPricing;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.infrastructure.Charger;

public class ChargerPricingProfile{
	
	private final Id<Charger> chargerId;
	private final String zoneId;
	private Map<Integer,double[]> pricingProfile = new HashMap<>();
	private double profileTimeStepInMin = 15;
	private Map<String,Object> attributes = new HashMap<>();
	
	/**
	 * 
	 * @param chargerId
	 * @param zoneId
	 */
	public ChargerPricingProfile(Id<Charger> chargerId,String zoneId, double profileTimeStepInMin) {
		this.chargerId = chargerId;
		this.zoneId = zoneId;
		this.profileTimeStepInMin = profileTimeStepInMin;
		for(int i=0;i<24;i++) {//this is to create a flat one day price profile 
			double[] p = new double[2];// two steps
			pricingProfile.put(i,p);
		}
	}
	
	public void addHourlyPricingProfile(int hour, double[] pricingProfile) {
		this.pricingProfile.put(hour, pricingProfile);
	}
	
	/**
	 * Here non linear pricing means a profile that will be applied to all 24 hours
	 * @param chargerId
	 * @param zoneId
	 * @param nonLinearPrice
	 */
	public ChargerPricingProfile(Id<Charger> chargerId,String zoneId,double[] nonLinearPrice, double profileTimeStepInMin) {
		this.chargerId = chargerId;
		this.zoneId = zoneId;
		this.profileTimeStepInMin = profileTimeStepInMin;
		for(int i=0;i<24;i++) {//this is to create a flat one day price profile 
			
			pricingProfile.put(i,nonLinearPrice);
		}
	}
	
	/**
	 * Here price is a flat cost that ev pays irrespective of hour
	 * @param chargerId
	 * @param zoneId
	 * @param price
	 */
	public ChargerPricingProfile(Id<Charger> chargerId,String zoneId,double price, double profileTimeStepInMin) {
		this.chargerId = chargerId;
		this.zoneId = zoneId;
		this.profileTimeStepInMin = profileTimeStepInMin;
		for(int i=0;i<24;i++) {//this is to create a flat one day price profile 
			double[] p = new double[2];
			p[0] = price;
			p[1] = price;
			pricingProfile.put(i,p);
		}
	}
	
	/**
	 * Dynamic pricing including hourly non linear p
	 * @param chargerId
	 * @param zoneId
	 * @param dynamicPricing
	 */
	public ChargerPricingProfile(Id<Charger> chargerId,String zoneId,Map<Integer,double[]>dynamicPricing, double profileTimeStepInMin) {
		this.chargerId = chargerId;
		this.zoneId = zoneId;
		this.pricingProfile = dynamicPricing;
		this.profileTimeStepInMin = profileTimeStepInMin;
	}

	public Map<Integer, double[]> getPricingProfile() {
		return pricingProfile;
	}

	public void setPricingProfile(Map<Integer, double[]> pricingProfile) {
		this.pricingProfile = pricingProfile;
	}

	public Id<Charger> getChargerId() {
		return chargerId;
	}

	public String getZoneId() {
		return zoneId;
	}

	public double getProfileTimeStepInMin() {
		return profileTimeStepInMin;
	}

	public void setProfileTimeStepInMin(double profileTimeStepInMin) {
		this.profileTimeStepInMin = profileTimeStepInMin;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}
	public int getPricingStepsSize() {
		return this.pricingProfile.get(0).length;
	}

}
