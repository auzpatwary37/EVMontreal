package EVPricing;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;



public class ChargerPricingProfileReader  extends DefaultHandler{
	
	private Map<Id<Charger>,ChargerPricingProfile> profiles = new HashMap<>();
	private Id<Charger> currentChargerId = null;
	private Map<String,Coord> zones = new HashMap<>();
	
	

	@Override 
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		if(qName.equalsIgnoreCase("zone")) {
			this.zones.put(attributes.getValue("zoneId"),new Coord(Double.parseDouble(attributes.getValue("X")),Double.parseDouble(attributes.getValue("Y"))));
		}
		if(qName.equalsIgnoreCase("ChargerPricingProfile")) {
			this.currentChargerId = Id.create(attributes.getValue("chargerId"), Charger.class);
			this.profiles.put(currentChargerId, new ChargerPricingProfile(this.currentChargerId,attributes.getValue("zoneId"),Double.parseDouble(attributes.getValue("profileTimeStepInMin"))));
			for(String pId:attributes.getValue("personAccesibleTo").split(",")) {
				if(!pId.equals("")) {
					this.profiles.get(currentChargerId).addPerson(Id.createPersonId(pId));
				}
			}
		}
		if(qName.equalsIgnoreCase("HourlyProfile")) {
			String[] pricingProfile = attributes.getValue("profile").split(",");
			double[] pp = new double[pricingProfile.length];
			for(int i=0;i<pricingProfile.length;i++)pp[i] = Double.parseDouble(pricingProfile[i]);
			this.profiles.get(currentChargerId).addHourlyPricingProfile(Integer.parseInt(attributes.getValue("Hour")), pp);
			this.profiles.get(currentChargerId).setSwitch(Integer.parseInt(attributes.getValue("Hour")), Boolean.parseBoolean(attributes.getValue("chargerSwitch")));
		}
	}
	
	@Override 
	public void endElement(String uri, String localName, String qName) {
		
	}
	
	public ChargerPricingProfiles readChargerPricingProfiles(String fileLoc) {
		
		try {
			SAXParserFactory.newInstance().newSAXParser().parse(fileLoc,this);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		ChargerPricingProfiles p = new ChargerPricingProfiles(this.zones);
		this.profiles.values().stream().forEach(pp->p.addChargerPricingProfile(pp));
		return p;
	}
}
