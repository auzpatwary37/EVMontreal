package binding;
/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** *
 */


import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.charging.ChargingHandler;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.charging.FixedSpeedCharging;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

import com.google.inject.Inject;
import com.google.inject.Provider;

import urbanEV.UrbanEVConfigGroup;

/**
 * @author Michal Maciejewski (michalm)
 */
public class ChargingModule extends AbstractModule {
	@Override
	public void install() {
		bind(ChargingLogic.Factory.class).toProvider(new Provider<>() {
			@Inject
			private EventsManager eventsManager;
			
			@Inject
			private Scenario scenario;
			
			@Inject
			private UrbanEVConfigGroup urbanEVConfig;

			@Override
			public ChargingLogic.Factory get() {
				return charger -> new ChargingWithQueueingLogic(charger, new ChargeUptoFixedSocStrategy(charger, 1.,1.0),
						eventsManager,scenario, urbanEVConfig);
			}
		});

		bind(ChargingPower.Factory.class).toInstance(ev -> new FixedSpeedCharging(ev, 1));
		
		installQSimModule(new AbstractQSimModule() {
			@Override
			protected void configureQSim() {
				this.bind(ChargingHandler.class).asEagerSingleton();
			//	this.bind(EVOutOfBatteryChecker.class).asEagerSingleton();
				this.addQSimComponentBinding(EvModule.EV_COMPONENT).to(ChargingHandler.class);
				this.addQSimComponentBinding(EvModule.EV_COMPONENT+"dis").to(EVOutOfBatteryChecker.class);
			}
		});
		//this.addEventHandlerBinding().to(EVOutOfBatteryChecker.class);
	}
}