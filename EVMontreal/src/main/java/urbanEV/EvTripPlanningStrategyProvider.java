package urbanEV;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.ReplanningContext;
import org.matsim.core.replanning.selectors.ExpBetaPlanSelector;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class EvTripPlanningStrategyProvider implements Provider<PlanStrategy> {
	public final static String PLUGIN = "plugin";
	public final static String PLUGOUT = "plugout";
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
        
        builder.addStrategyModule(new PlanStrategyModule() {

			@Override
			public void prepareReplanning(ReplanningContext replanningContext) {
				
			}

			@Override
			public void handlePlan(Plan plan) {
				makeChargingNotStaged(plan);
				//plan.getPlanElements().stream().filter(a-> a instanceof Activity).forEach(a->a.getAttributes().removeAttribute("actSOC"));
			}

			@Override
			public void finishReplanning() {
			
			}
        	
        });
        //eventsManager.addHandler(changeChargingBehaviourModule);
        return builder.build();
    }
	public static void makeChargingNotStaged(Plan plan) {
		plan.getPlanElements().stream().filter(pe->pe instanceof Activity).forEach(pe->{
			if(((Activity)pe).getType().contains(PlanCalcScoreConfigGroup.createStageActivityType(PLUGIN))) {
				((Activity)pe).setType(((Activity)pe).getType().replace(" interaction", ""));
			}else if(((Activity)pe).getType().contains(PlanCalcScoreConfigGroup.createStageActivityType(PLUGOUT))) {
				((Activity)pe).setType(((Activity)pe).getType().replace(" interaction", ""));
			}
		});
		}
}
