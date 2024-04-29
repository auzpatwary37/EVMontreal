//package withinDay;
//
//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.population.Activity;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.contrib.ev.fleet.ElectricFleetReader;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
//import org.matsim.contrib.ev.fleet.ElectricVehicle;
//import org.matsim.core.population.PopulationUtils;
//import org.matsim.core.utils.misc.OptionalTime;
//
//public class AgentSOCReader {
//    public static void main(String[] args) throws IOException {
//        Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\KWUsage&TimeBaseScenarioAllHomeChargersAllLogicRevisedPunishmentPricedRevised2\\output_plans.xml.gz");
//        ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
//        new ElectricFleetReader(evs).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml");
//
//        // Create a map to store actSOC for each agent in each time
//        Map<Id<Person>, Map<Double, Double>> agentSOCMap = new HashMap<>();
//
//        population.getPersons().values().forEach(person -> {
//            Map<Double, Double> agentTimeSOCMap = new HashMap<>();
//            agentSOCMap.put(person.getId(), agentTimeSOCMap);
//
//            person.getSelectedPlan().getPlanElements().forEach(planElement -> {
//                if (planElement instanceof Activity) {
//                    Activity activity = (Activity) planElement;
//                    if (activity.getAttributes().getAttribute("actSOC") != null) {
//                        double actSoc = (double) activity.getAttributes().getAttribute("actSOC");
//
//                        OptionalTime time = activity.getStartTime();
//                        double t = time.isDefined() ? time.seconds() : 0;
//                        double T = t;
//
//                        agentTimeSOCMap.put(T, actSoc);
//                    }
//                }
//            });
//        });
//
//        // Write agent SOC for each time to a CSV file
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\agentSOCPriced.csv"))) {
//            writer.write("AgentID, Time, ActSOC\n");
//
//            for (Map.Entry<Id<Person>, Map<Double, Double>> entry : agentSOCMap.entrySet()) {
//                Id<Person> agentId = entry.getKey();
//                Map<Double, Double> timeSOCMap = entry.getValue();
//
//                for (Map.Entry<Double, Double> timeSOCEntry : timeSOCMap.entrySet()) {
//                    double time = timeSOCEntry.getKey();
//                    double actSOC = timeSOCEntry.getValue();
//
//                    writer.write(agentId + ", " + time + ", " + actSOC + "\n");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}

//package withinDay;
//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.population.Activity;
//import org.matsim.api.core.v01.population.Leg;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.contrib.ev.fleet.ElectricFleetReader;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
//import org.matsim.contrib.ev.fleet.ElectricVehicle;
//import org.matsim.core.population.PopulationUtils;
//import org.matsim.core.utils.misc.OptionalTime;
//
//public class AgentSOCReader {
//    public static void main(String[] args) throws IOException {
//        Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\KWUsage&TimeBaseScenarioAllHomeChargersAllLogicRevisedPunishmentFlat\\output_plans.xml.gz");
//        ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
//        new ElectricFleetReader(evs).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml");
//
//        // Create a map to store actSOC, activity type, and mode for each agent in each time
//        Map<Id<Person>, Map<Double, Map<String, String>>> agentActivityMap = new HashMap<>();
//
//        population.getPersons().values().forEach(person -> {
//            Map<Double, Map<String, String>> agentTimeActivityMap = new HashMap<>();
//            agentActivityMap.put(person.getId(), agentTimeActivityMap);
//
//            person.getSelectedPlan().getPlanElements().forEach(planElement -> {
//                if (planElement instanceof Activity) {
//                    Activity activity = (Activity) planElement;
//                    String activityType = activity.getType();
//
//                    OptionalTime activityStartTime = activity.getStartTime();
//                    double activityStartSeconds = activityStartTime.isDefined() ? activityStartTime.seconds() : 0;
//                    double activityStartTimeIndex = activityStartSeconds / 900;
//
//                    // Check if the map for this time already exists
//                    if (!agentTimeActivityMap.containsKey(activityStartTimeIndex)) {
//                        agentTimeActivityMap.put(activityStartTimeIndex, new HashMap<>());
//                    }
//
//                    // Put activity type for this time
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("ActivityType", activityType);
//
//                    // Get the mode or leg for this activity
//                    String mode = null;
//                    if (planElement instanceof Leg) {
//                        Leg leg = (Leg) planElement;
//                        mode = leg.getMode();
//                    }
//
//                    // Put mode for this time
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("Mode", mode);
//                }
//            });
//        });
//
//        // Write agent activity information for each time to a CSV file
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output/agentActivity.csv"))) {
//            writer.write("AgentID, Time, ActivityType, Mode\n");
//
//            for (Map.Entry<Id<Person>, Map<Double, Map<String, String>>> entry : agentActivityMap.entrySet()) {
//                Id<Person> agentId = entry.getKey();
//                Map<Double, Map<String, String>> timeActivityMap = entry.getValue();
//
//                for (Map.Entry<Double, Map<String, String>> timeActivityEntry : timeActivityMap.entrySet()) {
//                    double time = timeActivityEntry.getKey();
//                    String activityType = timeActivityEntry.getValue().get("ActivityType");
//                    String mode = timeActivityEntry.getValue().get("Mode");
//
//                    writer.write(agentId + ", " + time + ", " + activityType + ", " + mode + "\n");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}

//package withinDay;
//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.population.Activity;
//import org.matsim.api.core.v01.population.Leg;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.contrib.ev.fleet.ElectricFleetReader;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
//import org.matsim.core.population.PopulationUtils;
//import org.matsim.core.utils.misc.OptionalTime;
//
//public class AgentSOCReader {
//    public static void main(String[] args) throws IOException {
//        Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\KWUsage&TimeBaseScenarioAllHomeChargersAllLogicRevisedPunishmentPricedRevised2\\output_plans.xml.gz");
//        ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
//        new ElectricFleetReader(evs).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml");
//
//        // Create a map to store actSOC, activity type, mode, and the previous leg mode for each agent in each time
//        Map<Id<Person>, Map<Double, Map<String, String>>> agentActivityMap = new HashMap<>();
//
//        population.getPersons().values().forEach(person -> {
//            Map<Double, Map<String, String>> agentTimeActivityMap = new HashMap<>();
//            agentActivityMap.put(person.getId(), agentTimeActivityMap);
//
//            final Leg[] prevLeg = {null}; // To store the previous leg
//
//            person.getSelectedPlan().getPlanElements().forEach(planElement -> {
//                if (planElement instanceof Activity) {
//                    Activity activity = (Activity) planElement;
//                    String activityType = activity.getType();
//
//                    OptionalTime activityStartTime = activity.getStartTime();
//                    double activityStartSeconds = activityStartTime.isDefined() ? activityStartTime.seconds() : 0;
//                    double activityStartTimeIndex = activityStartSeconds / 900;
//
//                    // Check if the map for this time already exists
//                    if (!agentTimeActivityMap.containsKey(activityStartTimeIndex)) {
//                        agentTimeActivityMap.put(activityStartTimeIndex, new HashMap<>());
//                    }
//
//                    // Put activity type for this time
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("ActivityType", activityType);
//
//                    // Get the mode for the previous leg
//                    String prevLegMode = prevLeg[0] != null ? prevLeg[0].getMode() : "N/A";
//
//                    // Put the mode for the previous leg
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("PrevLegMode", prevLegMode);
//                } else if (planElement instanceof Leg) {
//                    prevLeg[0] = (Leg) planElement; // Update the previous leg
//                }
//            });
//        });
//
//        // Write agent activity information for each time to a CSV file
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\agentActivityPriced.csv"))) {
//            writer.write("AgentID, Time, ActivityType, PrevLegMode\n");
//
//            for (Map.Entry<Id<Person>, Map<Double, Map<String, String>>> entry : agentActivityMap.entrySet()) {
//                Id<Person> agentId = entry.getKey();
//                Map<Double, Map<String, String>> timeActivityMap = entry.getValue();
//
//                for (Map.Entry<Double, Map<String, String>> timeActivityEntry : timeActivityMap.entrySet()) {
//                    double time = timeActivityEntry.getKey();
//                    String activityType = timeActivityEntry.getValue().getOrDefault("ActivityType", "N/A");
//                    String prevLegMode = timeActivityEntry.getValue().getOrDefault("PrevLegMode", "N/A");
//
//                    writer.write(agentId + ", " + time + ", " + activityType + ", " + prevLegMode + "\n");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}


//package withinDay;
//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.population.Activity;
//import org.matsim.api.core.v01.population.Leg;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.contrib.ev.fleet.ElectricFleetReader;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
//import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
//import org.matsim.core.population.PopulationUtils;
//import org.matsim.core.utils.misc.OptionalTime;
//
//public class AgentSOCReader {
//    public static void main(String[] args) throws IOException {
//        Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\KWUsage&TimeBaseScenarioAllHomeChargersAllLogicRevisedPunishmentPricedRevised2\\output_plans.xml.gz");
//        ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
//        new ElectricFleetReader(evs).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml");
//
//        // Create a map to store actSOC, activity type, mode, and the previous leg mode for each agent in each time
//        Map<Id<Person>, Map<Double, Map<String, String>>> agentActivityMap = new HashMap<>();
//
//        population.getPersons().values().forEach(person -> {
//            Map<Double, Map<String, String>> agentTimeActivityMap = new HashMap<>();
//            agentActivityMap.put(person.getId(), agentTimeActivityMap);
//
//            final Leg[] prevLeg = {null}; // To store the previous leg
//
//            person.getSelectedPlan().getPlanElements().forEach(planElement -> {
//                if (planElement instanceof Activity) {
//                    Activity activity = (Activity) planElement;
//                    String activityType = activity.getType();
//
//                    OptionalTime activityStartTime = activity.getStartTime();
//                    double activityStartSeconds = activityStartTime.isDefined() ? activityStartTime.seconds() : 0;
//                    double activityStartTimeIndex = activityStartSeconds / 900;
//
//                    // Check if the map for this time already exists
//                    if (!agentTimeActivityMap.containsKey(activityStartTimeIndex)) {
//                        agentTimeActivityMap.put(activityStartTimeIndex, new LinkedHashMap<>());
//                    }
//
//                    // Put activity type for this time
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("ActivityType", activityType);
//
//                    // Get the mode for the previous leg
//                    String prevLegMode = prevLeg[0] != null ? prevLeg[0].getMode() : "N/A";
//
//                    // Put the mode for the previous leg
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("PrevLegMode", prevLegMode);
//                    
//                    // Get actSOC attribute for the activity
//                    Double actSOC = (Double) activity.getAttributes().getAttribute("actSOC");
//
//                    // Put actSOC for this time
//                    agentTimeActivityMap.get(activityStartTimeIndex).put("ActSOC", String.valueOf(actSOC));
//                } else if (planElement instanceof Leg) {
//                    prevLeg[0] = (Leg) planElement; // Update the previous leg
//                }
//            });
//        });
//
//        // Write agent activity information for each time to a CSV file
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\agentActivitySOC&ModePriced.csv"))) {
//            writer.write("AgentID, Time, ActivityType, PrevLegMode, ActSOC\n");
//
//            for (Map.Entry<Id<Person>, Map<Double, Map<String, String>>> entry : agentActivityMap.entrySet()) {
//                Id<Person> agentId = entry.getKey();
//                Map<Double, Map<String, String>> timeActivityMap = entry.getValue();
//
//                for (Map.Entry<Double, Map<String, String>> timeActivityEntry : timeActivityMap.entrySet()) {
//                        String activityType = activity.getType();
//                        OptionalTime activityStartTime = activity.getStartTime();
//                        double startTime = activityStartTime.isDefined() ? activityStartTime.seconds() : 0.0;
//                	
//                	
//                	double time = timeActivityEntry.getKey();
//                    String activityType = timeActivityEntry.getValue().getOrDefault("ActivityType", "N/A");
//                    String prevLegMode = timeActivityEntry.getValue().getOrDefault("PrevLegMode", "N/A");
//                    String actSOC = timeActivityEntry.getValue().getOrDefault("ActSOC", "N/A");
//
//                    writer.write(agentId + ", " + time + ", " + activityType + ", " + prevLegMode + ", " + actSOC + "\n");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}





//import java.io.BufferedWriter;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.population.Activity;
//import org.matsim.api.core.v01.population.Leg;
//import org.matsim.api.core.v01.population.Person;
//import org.matsim.api.core.v01.population.Population;
//import org.matsim.core.population.PopulationUtils;
//import org.matsim.core.utils.misc.OptionalTime;
//
//public class AgentActivitiesOrderWriter {
//    public static void main(String[] args) throws IOException {
//        Population population = PopulationUtils.readPopulation("path_to_your_population_file.xml.gz");
//
//        // Create a map to store activities for each agent
//        Map<Id<Person>, List<Activity>> agentActivityMap = new HashMap<>();
//
//        // Iterate through each person
//        for (Person person : population.getPersons().values()) {
//            Id<Person> personId = person.getId();
//            List<Activity> activities = new ArrayList<>();
//
//            // Iterate through the plan elements for the person
//            for (PlanElement planElement : person.getSelectedPlan().getPlanElements()) {
//                if (planElement instanceof Activity) {
//                    activities.add((Activity) planElement);
//                }
//            }
//
//            // Put the activities in the map for the person
//            agentActivityMap.put(personId, activities);
//        }
//
//        // Write agent activity information to a CSV file
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter("path_to_output_file.csv"))) {
//            writer.write("AgentID, ActivityType, StartTime\n");
//
//            // Iterate through the agent-activity map
//            for (Map.Entry<Id<Person>, List<Activity>> entry : agentActivityMap.entrySet()) {
//                Id<Person> agentId = entry.getKey();
//                List<Activity> activities = entry.getValue();
//
//                // Iterate through the activities for each agent
//                for (Activity activity : activities) {
//                    String activityType = activity.getType();
//                    OptionalTime activityStartTime = activity.getStartTime();
//                    double startTime = activityStartTime.isDefined() ? activityStartTime.seconds() : 0.0;
//
//                    writer.write(agentId + ", " + activityType + ", " + startTime + "\n");
//                }
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}


package withinDay;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.ev.fleet.ElectricFleetReader;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecificationImpl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.misc.OptionalTime;

public class AgentSOCReader {
    public static void main(String[] args) throws IOException {
        Population population = PopulationUtils.readPopulation("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\KWUsage&TimeBaseScenarioAllHomeChargersAllLogicRevisedPunishmentFlat\\output_plans.xml.gz");
        ElectricFleetSpecification evs = new ElectricFleetSpecificationImpl();
        new ElectricFleetReader(evs).readFile("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\evehicle.xml");

        // Create a map to store actSOC, activity type, mode, and the previous leg mode for each agent in each time
        Map<Id<Person>, Map<Double, Map<String, String>>> agentActivityMap = new HashMap<>();

        population.getPersons().values().forEach(person -> {
            Map<Double, Map<String, String>> agentTimeActivityMap = new LinkedHashMap<>(); // Use LinkedHashMap to maintain insertion order
            agentActivityMap.put(person.getId(), agentTimeActivityMap);

            final Leg[] prevLeg = {null}; // To store the previous leg

            person.getSelectedPlan().getPlanElements().forEach(planElement -> {
                if (planElement instanceof Activity) {
                    Activity activity = (Activity) planElement;
                    String activityType = activity.getType();

                    OptionalTime activityStartTime = activity.getStartTime();
                    double activityStartSeconds = activityStartTime.isDefined() ? activityStartTime.seconds() : 0;
//                    double activityStartTimeIndex = activityStartSeconds / 900;

                    // Put activity type for this time
                    Map<String, String> activityAttributes = new HashMap<>();
                    activityAttributes.put("ActivityType", activityType);

                    // Get the mode for the previous leg
                    String prevLegMode = prevLeg[0] != null ? prevLeg[0].getMode() : "N/A";
                    activityAttributes.put("PrevLegMode", prevLegMode);

                    // Get actSOC attribute for the activity
                    Double actSOC = (Double) activity.getAttributes().getAttribute("actSOC");
                    activityAttributes.put("ActSOC", String.valueOf(actSOC));

                    // Put activity attributes in the map
                    agentTimeActivityMap.put(activityStartSeconds, activityAttributes);
                } else if (planElement instanceof Leg) {
                    prevLeg[0] = (Leg) planElement; // Update the previous leg
                }
            });
        });

        // Write agent activity information for each time to a CSV file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\arsha\\git\\EVMontreal\\EVMontreal\\ABMTrans\\output\\agentActivitySOC&ModeFlat.csv"))) {
            writer.write("AgentID, Time, ActivityType, PrevLegMode, ActSOC\n");

            for (Map.Entry<Id<Person>, Map<Double, Map<String, String>>> entry : agentActivityMap.entrySet()) {
                Id<Person> agentId = entry.getKey();
                Map<Double, Map<String, String>> timeActivityMap = entry.getValue();

                for (Map.Entry<Double, Map<String, String>> timeActivityEntry : timeActivityMap.entrySet()) {
                    double time = timeActivityEntry.getKey();
                    Map<String, String> activityAttributes = timeActivityEntry.getValue();
                    String activityType = activityAttributes.getOrDefault("ActivityType", "N/A");
                    String prevLegMode = activityAttributes.getOrDefault("PrevLegMode", "N/A");
                    String actSOC = activityAttributes.getOrDefault("ActSOC", "N/A");

                    writer.write(agentId + ", " + time + ", " + activityType + ", " + prevLegMode + ", " + actSOC + "\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}



