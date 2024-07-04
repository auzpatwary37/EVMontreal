package aiagent;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class AIAgentStrategyProvider implements Provider<PlanStrategy> {

	    private EventsManager eventsManager;
	    private Scenario scenario;
	    private AIAgentReplanningModule module;
		@Inject private GlobalConfigGroup globalConfigGroup;
		@Inject private ActivityFacilities facilities;
		@Inject private Provider<TripRouter> tripRouterProvider;
		@Inject private TimeInterpretation timeInterpretation;

	    @Inject
	    public AIAgentStrategyProvider(EventsManager eventsManager, Scenario scenario, AIAgentReplanningModule module) {
	        this.eventsManager = eventsManager;
	        this.scenario = scenario;
	        this.module = module;
	    }

	    @Override
	    public PlanStrategy get() {
	        PlanStrategyImpl.Builder builder = new PlanStrategyImpl.Builder(new RandomPlanSelector<Plan,Person>());
	        
	        builder.addStrategyModule(module);
	        builder.addStrategyModule(new org.matsim.core.replanning.modules.ReRoute(facilities, tripRouterProvider, globalConfigGroup, timeInterpretation));
	        //eventsManager.addHandler(changeChargingBehaviourModule);
	        return builder.build();
	    }

	}
