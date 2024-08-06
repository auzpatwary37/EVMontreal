package urbanEV.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStartEventHandler;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.controler.IterationCounter;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.misc.Time;

import com.google.inject.Inject;

import urbanEV.UrbanVehicleChargingHandler;

public class ActsWhileChargingAnalyzer implements ActivityStartEventHandler, ActivityEndEventHandler, ChargingStartEventHandler, IterationEndsListener {

    @Inject
    OutputDirectoryHierarchy controlerIO;
    @Inject
    IterationCounter iterationCounter;
    @Inject
    Scenario scenario;

   private Map<Id<Person>, List<String>> actsPerPersons = new ConcurrentHashMap<>();
   private Map<Id<Charger>, Id<Link>> chargersAtLinks = new ConcurrentHashMap<>();
   static Map<Id<Person>,Container> containers = new ConcurrentHashMap<>();
   static Map<Id<Person>,List<PersonContainer>> personContainers = new ConcurrentHashMap<>();

    /*
     *This class collects the activities while charging. Therefore it listens to the ActivityStartEvents and ChargingStartEvent.
     *Maybe this is a bit too inefficient coding but i couldn't find a other solution for the lifetime of the variables.
     * @author Jonas116
     */

    @Inject
    public ActsWhileChargingAnalyzer(ChargingInfrastructureSpecification chargingInfrastructureSpecification, Scenario scenario){

        for (Id<Person> personId : scenario.getPopulation().getPersons().keySet()) {
            List<String> acts = new ArrayList<>();
            Container container = new Container(personId,acts);
            containers.put(personId,container);

        }
        for (Id<Charger> chargerId : chargingInfrastructureSpecification.getChargerSpecifications().keySet()) {

            Id<Link> chargerLink = chargingInfrastructureSpecification.getChargerSpecifications().get(chargerId).getLinkId();
                                    
            chargersAtLinks.put(chargerId, chargerLink);
        }
    }


    @Override
    public void handleEvent(ActivityEndEvent event) {
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        if (!TripStructureUtils.isStageActivityType(event.getActType())) {
        	
            containers.get(event.getPersonId()).acts.add(event.getActType());

        }
        if (event.getActType().contains(UrbanVehicleChargingHandler.PLUGIN_IDENTIFIER)){
            String chargingActAndTime = event.getActType()+ event.getTime();
            containers.get(event.getPersonId()).acts.add(chargingActAndTime);

            
            PersonContainer personContainer = new PersonContainer(event.getPersonId().toString(), chargingActAndTime , event.getTime(), null);
            if(!personContainers.containsKey(event.getPersonId()))personContainers.put(event.getPersonId(),Collections.synchronizedList(new ArrayList<>()));
            personContainers.get(event.getPersonId()).add(personContainer);
        }

    }

    @Override
    public void handleEvent(ChargingStartEvent event) {

       Id<Person> personsId = Id.createPersonId(event.getVehicleId().toString());
       
        for (PersonContainer personContainer : this.personContainers.get(personsId)) {
            if (personContainer.time==event.getTime()){
                personContainer.setChargerId(event.getChargerId());
            }
        }


    }
    @Override
    public void reset(int iteration) {

        personContainers.clear();
        actsPerPersons.clear();
        this.containers.entrySet().forEach(e->e.getValue().acts.clear());
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {

        CSVPrinter csvPrinter = null;
        try {
            csvPrinter = new CSVPrinter(Files.newBufferedWriter(Paths.get(controlerIO.getIterationFilename(iterationCounter.getIterationNumber(), "actsPerCharger.csv"))), CSVFormat.DEFAULT.withDelimiter(';').
                    withHeader("PersonID", "ChargerId", "Activity type", "Time"));

            for (Entry<Id<Person>, List<PersonContainer>> personContainer : personContainers.entrySet()) {
                Id<Person> personId = personContainer.getKey();
                Person person = scenario.getPopulation().getPersons().get(personId);

                List<String> plan = containers.get(personId)
                        .acts;
                for(PersonContainer d:personContainer.getValue()) {
                if(this.containers.get(personId).acts.indexOf(d.chargingAct)!=this.containers.get(personId).acts.size()-1) {
                    csvPrinter.printRecord(personId, d.chargerId, plan.get(plan.indexOf(d.chargingAct) + 1), Time.writeTime(d.time));
                }
                }

            }


            csvPrinter.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }

    }





    private class Container  {
        private final Id<Person> personId;
        private final List<String> acts;

        Container (Id<Person> personId, List<String> acts){
            this.personId = personId;
            this.acts = acts;
        }
    }

    private  class PersonContainer{
        private final String personId;
        private final String chargingAct;
        private final double time;
        private  Id<Charger> chargerId;

        PersonContainer (String personId, String chargingAct, double time, Id<Charger> chargerId){
            this.personId = personId;
            this.chargingAct = chargingAct;
            this.time = time;
            this.chargerId = chargerId;
        }

        void setChargerId(Id<Charger> chargerId){
            this.chargerId = chargerId;
        }


    }






}
