/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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
 * *********************************************************************** */

package EVPricing;/*
 * created by jbischoff, 19.03.2019
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.charging.VehicleChargingHandler;
import org.matsim.contrib.ev.routing.EvNetworkRoutingProvider;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

public class RunEvExample {
	static final String DEFAULT_CONFIG_FILE = "montreal scenario5\\5_percent\\config.xml";
	private static final Logger log = Logger.getLogger(RunEvExample.class);

	public static void main(String[] args) throws IOException {
		final URL configUrl;
		if (args.length > 0) {
			log.info("Starting simulation run with the following arguments:");
			configUrl = new URL(args[0]);
			log.info("config URL: " + configUrl);
		} else {
			File localConfigFile = new File(DEFAULT_CONFIG_FILE);
			if (localConfigFile.exists()) {
				log.info("Starting simulation run with the local example config file");
				configUrl = localConfigFile.toURI().toURL();
			} else {
				log.info("Starting simulation run with the example config file from GitHub repository");
				configUrl = new URL("https://raw.githubusercontent.com/matsim-org/matsim/master/contribs/ev/"
						+ DEFAULT_CONFIG_FILE);
			}
		}
		new RunEvExample().run(configUrl);
	}

	public void run(URL configUrl) {
		Config config = ConfigUtils.loadConfig(configUrl, new EvConfigGroup());
		config.plans().setInputFile("plan.xml");
		((EvConfigGroup)config.getModules().get("ev")).setTimeProfiles(true);
		config.controler().setLastIteration(100);
		config.controler().setOutputDirectory("EV_5Percent_100Iter");
		config.controler()
				.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.qsim().setFlowCapFactor(0.1);
		config.qsim().setStorageCapFactor(0.1);
		config.global().setNumberOfThreads(11);
		config.qsim().setNumberOfThreads(6);
		
		
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		scaleDownPt(scenario.getTransitVehicles(), 0.1);
		
		config.planCalcScore().setPerforming_utils_hr(14);
		
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new EvModule());
		//controler.addOverridingModule(new EVPriceModule());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addRoutingModuleBinding(TransportMode.car).toProvider(new EvNetworkRoutingProvider(TransportMode.car));
				installQSimModule(new AbstractQSimModule() {
					@Override
					protected void configureQSim() {
						bind(VehicleChargingHandler.class).asEagerSingleton();
						bind(ChargePricingEventHandler.class).asEagerSingleton();
						addMobsimScopeEventHandlerBinding().to(VehicleChargingHandler.class);
						addMobsimScopeEventHandlerBinding().to(ChargePricingEventHandler.class);
					}
				});
			}
		});

		controler.configureQSimComponents(components -> components.addNamedComponent(EvModule.EV_COMPONENT));

		controler.run();
	}
	
	public static void scaleDownPt(Vehicles transitVehicles, double portion) {
		for(VehicleType vt: transitVehicles.getVehicleTypes().values()) {
			VehicleCapacity vc = vt.getCapacity();
			vc.setSeats((int) Math.ceil(vc.getSeats().intValue() * portion));
			vc.setStandingRoom((int) Math.ceil(vc.getStandingRoom().intValue() * portion));
			vt.setPcuEquivalents(vt.getPcuEquivalents() * portion);
		}
	}
}
