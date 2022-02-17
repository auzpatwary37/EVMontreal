package EVPricing;

import java.io.FileOutputStream;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
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
				
				Element profile=document.createElement("PricingProfile");
				for(Entry<Integer, double[]> e:pp.getValue().getPricingProfile().entrySet()) {
					Element volume=document.createElement("Hourly Profile");
					volume.setAttribute("Hour", Integer.toString(e.getKey()));
					String s = "";
					String sep = "";
					for(double d:e.getValue()) {
						s = s+sep+d;
						sep = ",";
					}
					volume.setAttribute("profile", s);
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
			System.out.println(e.getStackTrace());
		}

	}
}
