package binding;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

import com.google.inject.Inject;

import EVPricing.ChargePricingEventHandler;
import EVPricing.ChargerPricingProfileReader;
import EVPricing.ChargerPricingProfiles;
import urbanEV.UrbanEVConfigGroup;

public class ChargerPricingModule extends AbstractModule {
		
		@Inject
		private EvConfigGroup evCfg;
		
		@Inject
		private UrbanEVConfigGroup urbanEvCfg;
			
		

		@Override
		public void install() {
			bind(ChargerPricingProfiles.class).toProvider(() -> {
				ChargerPricingProfiles pricingProfiles = new ChargerPricingProfileReader().readChargerPricingProfiles(urbanEvCfg.getChargerPricingFileLocation());
				return pricingProfiles;
			}).asEagerSingleton();
			installQSimModule(new AbstractQSimModule() {
			@Override
			protected void configureQSim() {
				bind(ChargePricingEventHandler.class).asEagerSingleton();
				//addMobsimScopeEventHandlerBinding().to(VehicleChargingHandler.class);
				
				addMobsimScopeEventHandlerBinding().to(ChargePricingEventHandler.class);
				this.addQSimComponentBinding(EvModule.EV_COMPONENT).to(ChargePricingEventHandler.class);
				this.bind(EVOutOfBatteryChecker.class).asEagerSingleton();
				this.addQSimComponentBinding(EvModule.EV_COMPONENT).to(EVOutOfBatteryChecker.class);
			}
		});
		}
			
			
	
}
