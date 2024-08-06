package modeChoiceFix;

import javax.inject.Provider;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

public class ReRouteEvExclutionModule extends AbstractMultithreadedModule {
	public static final String name = "Reroute ev";
	private ActivityFacilities facilities;
	private final TimeInterpretation timeInterpretation;

	private final Provider<TripRouter> tripRouterProvider;

	public ReRouteEvExclutionModule(ActivityFacilities facilities, Provider<TripRouter> tripRouterProvider, GlobalConfigGroup globalConfigGroup, TimeInterpretation timeInterpretation) {
		super(globalConfigGroup);
		this.facilities = facilities;
		this.tripRouterProvider = tripRouterProvider;
		this.timeInterpretation = timeInterpretation;
	}

	public ReRouteEvExclutionModule(Scenario scenario, Provider<TripRouter> tripRouterProvider, TimeInterpretation timeInterpretation) {
		this(scenario.getActivityFacilities(), tripRouterProvider, scenario.getConfig().global(), timeInterpretation);
	}

	@Override
	public final PlanAlgorithm getPlanAlgoInstance() {
			return new PlanRouterEv(
					tripRouterProvider.get(),
					facilities,
					timeInterpretation
					);
	}

}
