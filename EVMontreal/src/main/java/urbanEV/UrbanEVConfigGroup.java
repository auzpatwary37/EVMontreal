/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package urbanEV;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.HashSet;
import java.util.Set;


//TODO add decriptions
public class UrbanEVConfigGroup extends ReflectiveConfigGroup {

    //TODO should we rename the entire package from UrbanEV to EVChargingPreplanning or something similar?
    static final String GROUP_NAME = "urbanEV" ;



    public UrbanEVConfigGroup() {
        super(GROUP_NAME);

    }
    
    private static final String PRICING_LOGIC = "pricingLogic";
    private static final String CHARGING_LOGIC = "chargingLogic";
    private static final String AUTOMATIC_KICK_OUT_FROM_CHARGER_QUEUE = "automaticKickOutFromChargerQueue";
    private static final String RANGE_ANXIETY_CONST_A = "rangeAnxietyConstA";
    private static final String RANGE_ANXIETY_CONST_B = "rangeAnxietyConstB";
    private static final String CHARGER_PRICING_FILE_LOCATION = "chargerPricingFileLocation";

    /**
     * Determines which pricing logic to use. Choose from Usage-Based Pricing, Time-Based Pricing and Combined Pricing. in usage based people a
     * are charged for the power they have used. In time based they are charged based on the time they are plugged in. In combined both are calculated and 
     * the max of the two is payable. 
     */
    private PricingLogic pricingLogic = PricingLogic.USAGE_BASED;
    /**
     * Determines the logic people will follow while replanning for charging. Choose from Experience-Based Charging, Duration-Based Charging, Optimized Charging or 
     * Combined Random Charging. In the experience based one, people plan charging based on their experience based soc at each activity. In duration based, people 
     * tend to choose the activity with more duration to charge. In optimized, people randomly choose different activities to charge around and the matsim system 
     * picks the optimal plan. Finally, the random combined combine the different charging logics with uniform probability for each person, creating a heterogeneous charging behavior.  
     */
    private PersonChargingLogic chargingLogic = PersonChargingLogic.EXPERIENCE_BASED;
    
    /**
     * This variable controls if vehicles are automatically kicked out from chargers if charging is complete. 
     */
    private boolean automaticKickOutFromChargerQueue = false;
    /**
     * coefficient of the range anxiety function.
     */
    private double rangeAnxietyConstA = 0.00001;
    /**
     * Coefficient of the range anxiety function. 
     */
    private double rangeAnxietyConstB = 20;
    /**
     * Location for the charger pricing file.
     */
    private String chargerPricingFileLocation = null;


    private static final String MAXIMUM_CHARGING_PROCEDURES = "maximumChargingProceduresPerAgent";
    /**
     * determines how often an agent can charge per day at max. During the pre-planning process, the agent has to go through it's entire plan n + 1 times where n is the
     * number of charging procedures he ends up with.
     */
    private int maximumChargingProceduresPerAgent = 3;

    private static final String CRITICAL_RELATIVE_SOC = "criticalRelativeSOC";
    /**
     * agents intend to charge their vehicle prior to the relative SOC falling under the given value
     */
    private double criticalRelativeSOC = 0.5;

    private static final String MIN_WHILE_CHARGING_ACT_DURATION_s = "minWhileChargingActivityDuration_s";
    /**
     * determines the minimum duration for activities to be determined suitable for charging the vehicle during the performance of the activity. In seconds.
     */
    private double minWhileChargingActivityDuration_s = 20 * 60;

    private static final String WHILE_CHARGING_ACT_TYPES = "whileChargingActivityTypes";
    /**
     * the activity types during which agents can charge their vehicle
     */
    private Set<String> whileChargingActivityTypes = new HashSet<>();

    private double maxDistanceBetweenActAndCharger_m = 500;
    /**
     * determines the maximum distance between act while charging and charger
     */
    private static final String MAXIMUM_DISTANCE_TO_CHARGER ="maxDistanceToCharger";

    private static boolean pluginBeforeStartingThePlan = true;

    /**
     * determines the plug in act before the start of the plan to simulate charging between last act of the plan and first
     */

    private static final String PLUGIN_BEFORE_STARTING_THE_PLAN = "pluginBeforeTheStartingThePlan";
    
    

    //-------------------------------------------------------------------------------------------
    
    //@StringGetter(PRICING_LOGIC)
    public PricingLogic getPricingLogic() {
        return pricingLogic;
    }

    //@StringSetter(PRICING_LOGIC)
    public void setPricingLogic(PricingLogic pricingLogic) {
        this.pricingLogic = pricingLogic;
    }

    //@StringGetter(CHARGING_LOGIC)
    public PersonChargingLogic getChargingLogic() {
        return chargingLogic;
    }

    //@StringSetter(CHARGING_LOGIC)
    public void setChargingLogic(PersonChargingLogic chargingLogic) {
        this.chargingLogic = chargingLogic;
    }

    //@StringGetter(AUTOMATIC_KICK_OUT_FROM_CHARGER_QUEUE)
    public boolean isAutomaticKickOutFromChargerQueue() {
        return automaticKickOutFromChargerQueue;
    }

    //@StringSetter(AUTOMATIC_KICK_OUT_FROM_CHARGER_QUEUE)
    public void setAutomaticKickOutFromChargerQueue(boolean automaticKickOutFromChargerQueue) {
        this.automaticKickOutFromChargerQueue = automaticKickOutFromChargerQueue;
    }

    //@StringGetter(RANGE_ANXIETY_CONST_A)
    public double getRangeAnxietyConstA() {
        return rangeAnxietyConstA;
    }

    //@StringSetter(RANGE_ANXIETY_CONST_A)
    public void setRangeAnxietyConstA(double rangeAnxietyConstA) {
        this.rangeAnxietyConstA = rangeAnxietyConstA;
    }

    //@StringGetter(RANGE_ANXIETY_CONST_B)
    public double getRangeAnxietyConstB() {
        return rangeAnxietyConstB;
    }

    //@StringSetter(RANGE_ANXIETY_CONST_B)
    public void setRangeAnxietyConstB(double rangeAnxietyConstB) {
        this.rangeAnxietyConstB = rangeAnxietyConstB;
    }

    //@StringGetter(CHARGER_PRICING_FILE_LOCATION)
    public String getChargerPricingFileLocation() {
        return chargerPricingFileLocation;
    }

    //@StringSetter(CHARGER_PRICING_FILE_LOCATION)
    public void setChargerPricingFileLocation(String chargerPricingFileLocation) {
        this.chargerPricingFileLocation = chargerPricingFileLocation;
    }


    //	@StringGetter(MAXIMUM_CHARGING_PROCEDURES)
    public int getMaximumChargingProceduresPerAgent() {
        return maximumChargingProceduresPerAgent;
    }

    //	@StringSetter(MAXIMUM_CHARGING_PROCEDURES)
    public void setMaximumChargingProceduresPerAgent(int maximumChargingProceduresPerAgent) {
        this.maximumChargingProceduresPerAgent = maximumChargingProceduresPerAgent;
    }

    //	@StringGetter(CRITICAL_RELATIVE_SOC)
    public double getCriticalRelativeSOC() {
        return criticalRelativeSOC;
    }

    //	@StringSetter(CRITICAL_RELATIVE_SOC)
    public void setCriticalRelativeSOC(double criticalRelativeSOC) {
        this.criticalRelativeSOC = criticalRelativeSOC;
    }

    //	@StringGetter(MIN_WHILE_CHARGING_ACT_DURATION_s)
    public double getMinWhileChargingActivityDuration_s() {
        return minWhileChargingActivityDuration_s;
    }

    //	@StringSetter(MIN_WHILE_CHARGING_ACT_DURATION_s)
    public void setMinWhileChargingActivityDuration_s(double minWhileChargingActivityDuration_s) {
        this.minWhileChargingActivityDuration_s = minWhileChargingActivityDuration_s;
    }

    //	@StringGetter(WHILE_CHARGING_ACT_TYPES)
    public Set<String> getWhileChargingActivityTypes() {
        return whileChargingActivityTypes;
    }

    //	@StringSetter(WHILE_CHARGING_ACT_TYPES)
    public void setWhileChargingActivityTypes(Set<String> whileChargingActivityTypes) {
        this.whileChargingActivityTypes = whileChargingActivityTypes;
    }

    // @StringGetter(MAXIMUM_DISTANCE_TO_CHARGER)
    public double getMaxDistanceBetweenActAndCharger_m(){
        return maxDistanceBetweenActAndCharger_m;
    }

    //  @StringSetter(MAXIMUM_DISTANCE_TO_CHARGER)
    public void setMaxDistanceBetweenActAndCharger_m(double maxDistanceBetweenActAndCharger_m){
        this.maxDistanceBetweenActAndCharger_m = maxDistanceBetweenActAndCharger_m;
    }


    //@StringGetter(PLUGIN_BEFORE_STARTING_THE_PLAN)
    public boolean getPluginBeforeStartingThePlan(){
        return pluginBeforeStartingThePlan;
    }

    //@StringSetter(PLUGIN_BEFORE_STARTING_THE_PLAN)
    public void setPluginBeforeStartingThePlan(boolean pluginBeforeStartingThePlan){
     this.pluginBeforeStartingThePlan = pluginBeforeStartingThePlan;
    }

   public static enum PricingLogic {
        USAGE_BASED("Usage-Based Pricing"),
        TIME_BASED("Time-Based Pricing"),
        COMBINED("Combined Pricing");

        private final String description;

        PricingLogic(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
    
    public static enum PersonChargingLogic {
        EXPERIENCE_BASED("Experience-Based Charging"),
        OPTIMIZED("Optimized Charging"),
        DURATION_BASED("Duration-Based Charging"),
        COMBINED_RANDOM("Combined Random Charging");

        private final String description;

        PersonChargingLogic(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
    
}
