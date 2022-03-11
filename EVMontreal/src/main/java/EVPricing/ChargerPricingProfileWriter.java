package EVPricing;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ChargerPricingProfileWriter {
	public final ChargerPricingProfiles profile;
	public ChargerPricingProfileWriter(ChargerPricingProfiles profile) {
		this.profile = profile;
	}
	
	
	public void write(String fileLoc) {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

			Document document = documentBuilder.newDocument();

			Element rootEle = document.createElement("ChargerPricingProfiles");

			Element zones=document.createElement("zones");
			for(Entry<String, Coord>z:profile.getZone().entrySet()) {
				Element zone=document.createElement("zone");
				zone.setAttribute("zoneId", z.getKey());
				zone.setAttribute("X", Double.toString(z.getValue().getX()));
				zone.setAttribute("Y", Double.toString(z.getValue().getY()));
				zones.appendChild(zone);
			}
			rootEle.appendChild(zones);
			
			for(Entry<Id<Charger>, ChargerPricingProfile> pp:profile.getChargerPricingProfiles().entrySet()) {
				Element pricingProfile=document.createElement("ChargerPricingProfile");
				pricingProfile.setAttribute("chargerId", pp.getKey().toString());
				pricingProfile.setAttribute("zoneId", pp.getValue().getZoneId());
				pricingProfile.setAttribute("profileTimeStepInMin", Double.toString(pp.getValue().getProfileTimeStepInMin()));
				String personIds = "";
				String sep = "";
				for(Id<Person> pId:pp.getValue().getPersonsAccecibleTo()) {
					personIds=personIds+sep+pId.toString();
					sep = ",";
				}
				pricingProfile.setAttribute("personAccesibleTo", personIds);
				Element profile=document.createElement("PricingProfile");
				for(Entry<Integer, double[]> e:pp.getValue().getPricingProfile().entrySet()) {
					Element volume=document.createElement("HourlyProfile");
					volume.setAttribute("Hour", Integer.toString(e.getKey()));
					String s = "";
					sep = "";
					if(e.getValue()==null) {
						System.out.println("debug!!!");
					}
					for(double d:e.getValue()) {
						s = s+sep+d;
						sep = ",";
					}
					volume.setAttribute("profile", s);
					volume.setAttribute("chargerSwitch", Boolean.toString(pp.getValue().getChargerSwitch().get(e.getKey())));
					profile.appendChild(volume);
				}
				pricingProfile.appendChild(profile);

				
				for(String s:pp.getValue().getAttributes().keySet()) {
					pricingProfile.setAttribute(s, pp.getValue().getAttributes().get(s).toString());
				}
				
				rootEle.appendChild(pricingProfile);
			}
			document.appendChild(rootEle);
			

			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			//tr.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "measurements.dtd");
			tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			tr.transform(new DOMSource(document), new StreamResult(new FileOutputStream(fileLoc)));


		}catch(Exception e) {
			System.out.println("Error while writing charger pricing file... Please check!!!");
			System.out.println(e);
		}
		
	}
}
