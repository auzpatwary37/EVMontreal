package modeChoiceFix;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.population.algorithms.PermissibleModesCalculatorImpl;

public class SubTourPlanStrategyBinder extends AbstractModule{

	private Config config;
	
	public SubTourPlanStrategyBinder(Config config) {
		this.config = config;
	}

	@Override
	public void install() {
		addPlanStrategyBinding(SubtourModeChoiceEv.name).toProvider(SubTourModeChoiceEvStrategyProvider.class);
		this.bind(PermissibleModesCalculator.class).toInstance(new PermissibleModesCalculatorImpl(config));
	}
	
	public static void addStrategy(Config config, double weight,int disableAfter) {
		StrategySettings s = new StrategySettings();
		s.setStrategyName(SubtourModeChoiceEv.name);
		s.setWeight(weight);
		s.setDisableAfter(disableAfter);
		config.strategy().addStrategySettings(s);
	}
	
	public static void configure(Controler c) {
		c.addOverridingModule(new SubTourPlanStrategyBinder(c.getConfig()));
	}

}
