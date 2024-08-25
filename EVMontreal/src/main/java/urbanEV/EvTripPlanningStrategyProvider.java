package urbanEV;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.selectors.ExpBetaPlanSelector;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class EvTripPlanningStrategyProvider implements Provider<PlanStrategy> {

    private EventsManager eventsManager;
    private Scenario scenario;
    private UrbanEVTripPlanningStrategyModule module;

    @Inject
    public EvTripPlanningStrategyProvider(EventsManager eventsManager, Scenario scenario, UrbanEVTripPlanningStrategyModule module) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.module = module;
    }

    @Override
    public PlanStrategy get() {
        double logitScaleFactor = 1.0;
        PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new ExpBetaPlanSelector<>(logitScaleFactor));
        
        builder.addStrategyModule(module);
        //eventsManager.addHandler(changeChargingBehaviourModule);
        return builder.build();
    }

}
