package binding;
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


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jcodec.common.logging.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.contrib.ev.charging.ChargingEndEvent;
import org.matsim.contrib.ev.charging.ChargingListener;
import org.matsim.contrib.ev.charging.ChargingLogic;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStrategy;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.core.api.experimental.events.EventsManager;

public class ChargingWithQueueingLogic implements ChargingLogic {
	private final ChargerSpecification charger;
	private final ChargingStrategy chargingStrategy;
	private final EventsManager eventsManager;
	private final Scenario scenario;
	private PopulationWriter popWriter;
	private final Map<Id<ElectricVehicle>, ElectricVehicle> pluggedVehicles = new ConcurrentHashMap<>();//Collections.synchronizedMap(new LinkedHashMap<>())
	private final Queue<ElectricVehicle> queuedVehicles = new ConcurrentLinkedQueue<>();
	private final Map<Id<ElectricVehicle>, ChargingListener> listeners = new ConcurrentHashMap<>();

	public ChargingWithQueueingLogic(ChargerSpecification charger, ChargingStrategy chargingStrategy,
			EventsManager eventsManager, Scenario scenario) {
		this.chargingStrategy = Objects.requireNonNull(chargingStrategy);
		this.charger = Objects.requireNonNull(charger);
		this.eventsManager = Objects.requireNonNull(eventsManager);
		this.scenario = scenario;
	}

	@Override
	public void chargeVehicles(double chargePeriod, double now) {
		Iterator<ElectricVehicle> evIter = pluggedVehicles.values().iterator();
//		while (evIter.hasNext()) {
//			ElectricVehicle ev = evIter.next();
//			// with fast charging, we charge around 4% of SOC per minute,
//			// so when updating SOC every 10 seconds, SOC increases by less then 1%
//			ev.getBattery().changeSoc(ev.getChargingPower().calcChargingPower(charger) * chargePeriod);
//
//			if (chargingStrategy.isChargingCompleted(ev)) {
//				evIter.remove();
//				Person person = scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString()));
//				ChargingEndEvent event = new ChargingEndEventUrbanEv(now, charger.getId(), ev.getId(),person);
//				scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString())).getAttributes().putAttribute("charginatLogicIndicator", false);
//				eventsManager.processEvent(event);
//				listeners.remove(ev.getId()).notifyChargingEnded(ev, now);
//			}
//		}
		
		for(Entry<Id<ElectricVehicle>, ElectricVehicle> evEntry:new HashMap<>(pluggedVehicles).entrySet()) {
			ElectricVehicle ev = evEntry.getValue();
			// with fast charging, we charge around 4% of SOC per minute,
			// so when updating SOC every 10 seconds, SOC increases by less then 1%
			ev.getBattery().changeSoc(ev.getChargingPower().calcChargingPower(charger) * chargePeriod);
//___________________________________________________comment these lines to not kick vehicles from queue_______________
//			if (chargingStrategy.isChargingCompleted(ev)) {
//				Person person = scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString()));
//				ChargingEndEvent event = new ChargingEndEventUrbanEv(now, charger.getId(), ev.getId(),person);
//				scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString())).getAttributes().putAttribute("charginatLogicIndicator", false);
//				eventsManager.processEvent(event);
//				listeners.remove(ev.getId()).notifyChargingEnded(ev, now);
//				pluggedVehicles.remove(evEntry.getKey());
//			}
//________________________________________________________________________________________________
		}

		int queuedToPluggedCount = Math.min(queuedVehicles.size(), charger.getPlugCount() - pluggedVehicles.size());
		for (int i = 0; i < queuedToPluggedCount; i++) {
			plugVehicle(queuedVehicles.poll(), now);
		}
	}

	@Override
	public synchronized void addVehicle(ElectricVehicle ev, double now) {
		addVehicle(ev, new ChargingListener() {}, now);
	}

	@Override
	public void addVehicle(ElectricVehicle ev, ChargingListener chargingListener, double now) {
		listeners.put(ev.getId(), chargingListener);
		if (pluggedVehicles.size() < charger.getPlugCount()) {
			plugVehicle(ev, now);
		} else {
			queueVehicle(ev, now);
		}
		if(pluggedVehicles.size()+this.queuedVehicles.size()!=this.listeners.size()) {
			System.out.println("Problem!!!");
		}
	}

	@Override
	public synchronized void removeVehicle(ElectricVehicle ev, double now) {
		if (pluggedVehicles.remove(ev.getId()) != null) {// successfully removed
			ChargingEndEvent event = new ChargingEndEventUrbanEv(now, charger.getId(), ev.getId(),scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString())));
			scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString())).getAttributes().putAttribute("charginatLogicIndicator", false);
			eventsManager.processEvent(event);
			listeners.remove(ev.getId()).notifyChargingEnded(ev, now);

			if (!queuedVehicles.isEmpty()) {
				plugVehicle(queuedVehicles.poll(), now);
			}
		} else if (queuedVehicles.remove(ev)) {//
			listeners.remove(ev.getId()).notifyChargingEnded(ev, now);
		} else {// neither plugged nor queued
			Logger.info("The vehicle is already plugged out.");
//			throw new IllegalArgumentException(
//					"Vehicle: " + ev.getId() + " is neither queued nor plugged at charger: " + charger.getId());
		}
	}

	private void queueVehicle(ElectricVehicle ev, double now) {
		queuedVehicles.add(ev);
		listeners.get(ev.getId()).notifyVehicleQueued(ev, now);
	}

	private void plugVehicle(ElectricVehicle ev, double now) {
		if (pluggedVehicles.put(ev.getId(), ev) != null) {
			throw new IllegalArgumentException();
		}
		eventsManager.processEvent(new ChargingStartEvent(now, charger.getId(), ev.getId(), charger.getChargerType()));
		//scenario.getPopulation().getPersons().get(Id.createPersonId(ev.getId().toString())).getAttributes().putAttribute("charginatLogicIndicator", true);
		listeners.get(ev.getId()).notifyChargingStarted(ev, now);
	}

	private final Collection<ElectricVehicle> unmodifiablePluggedVehicles = Collections.unmodifiableCollection(
			pluggedVehicles.values());

	@Override
	public Collection<ElectricVehicle> getPluggedVehicles() {
		return unmodifiablePluggedVehicles;
	}

	private final Collection<ElectricVehicle> unmodifiableQueuedVehicles = Collections.unmodifiableCollection(
			queuedVehicles);

	@Override
	public Collection<ElectricVehicle> getQueuedVehicles() {
		return unmodifiableQueuedVehicles;
	}

	@Override
	public ChargingStrategy getChargingStrategy() {
		return chargingStrategy;
	}
	
	
	public void reset(int i) {
		this.pluggedVehicles.clear();
		this.queuedVehicles.clear();
		this.listeners.clear();
	}
}
