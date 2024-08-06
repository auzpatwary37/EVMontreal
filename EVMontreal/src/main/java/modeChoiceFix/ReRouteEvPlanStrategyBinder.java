package modeChoiceFix;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;

public class ReRouteEvPlanStrategyBinder extends AbstractModule{

		private Config config;
		
		public ReRouteEvPlanStrategyBinder(Config config) {
			this.config = config;
		}

		@Override
		public void install() {
			addPlanStrategyBinding(ReRouteEvExclutionModule.name).toProvider(ReRouteEv.class);
		}
		
		public static void addStrategy(Config config, double weight,int disableAfter) {
			StrategySettings s = new StrategySettings();
			s.setStrategyName(ReRouteEvExclutionModule.name);
			s.setWeight(weight);
			s.setDisableAfter(disableAfter);
			config.strategy().addStrategySettings(s);
		}
		
		public static void configure(Controler c) {
			c.addOverridingModule(new ReRouteEvPlanStrategyBinder(c.getConfig()));
		}

	}

