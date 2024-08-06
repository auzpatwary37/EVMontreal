package modeChoiceFix;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

import aiagent.AIAgentReplanningModule;

public class PlanRouterEv extends PlanRouter{

	public PlanRouterEv(TripRouter tripRouter, ActivityFacilities facilities, TimeInterpretation timeInterpretation) {
		super(tripRouter, facilities, timeInterpretation);
	}

	
	@Override
	public void run(final Plan plan) {
		AIAgentReplanningModule.makeChargingNotStaged(plan);
		super.run(plan);
		AIAgentReplanningModule.makeChargingStaged(plan);
	}
}
