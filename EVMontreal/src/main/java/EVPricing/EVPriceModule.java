package EVPricing;

import org.matsim.core.controler.AbstractModule;

public class EVPriceModule extends AbstractModule {

	@Override
	public void install() {
		this.addEventHandlerBinding().to(ChargePricingEventHandler.class).asEagerSingleton();
	}

}
