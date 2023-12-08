package experienceSOC;

import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

public class ActivitySOCModule extends AbstractModule{

	@Override
	public void install() {
		installQSimModule(new AbstractQSimModule() {
			@Override
			protected void configureQSim() {
				//this is responsible for charging vehicles according to person activity start and end events..
				bind(ActivitySoc.class);
				addMobsimScopeEventHandlerBinding().to(ActivitySoc.class);

			}
		});
	}
	
	public static void configure(Controler c) {
		c.addOverridingModule(new ActivitySOCModule());
	}

}
