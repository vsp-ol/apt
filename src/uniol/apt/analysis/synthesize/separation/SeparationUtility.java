/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2014  Uli Schlachter
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package uniol.apt.analysis.synthesize.separation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import uniol.apt.adt.exception.StructureException;
import uniol.apt.adt.ts.Arc;
import uniol.apt.adt.ts.State;
import uniol.apt.analysis.synthesize.MissingLocationException;
import uniol.apt.analysis.synthesize.PNProperties;
import uniol.apt.analysis.synthesize.Region;
import uniol.apt.analysis.synthesize.RegionUtility;
import uniol.apt.util.DebugUtil;

/**
 * Helper functions for solving separation problems.
 * @author Uli Schlachter
 */
public class SeparationUtility extends DebugUtil {
	private SeparationUtility() {
	}

	/**
	 * Get the state which is reached by firing the given event in the given state.
	 * @param state The state to examine.
	 * @param event The event that should fire.
	 * @return The following state or zero.
	 */
	static public State getFollowingState(State state, String event) {
		for (Arc arc : state.getPostsetEdges())
			if (arc.getLabel().equals(event))
				return arc.getTarget();
		return null;
	}

	/**
	 * Test if there exists an outgoing arc labelled with the given event.
	 * @param state The state to examine.
	 * @param event The event that should fire.
	 * @return True if a suitable arc exists, else false.
	 */
	static public boolean isEventEnabled(State state, String event) {
		return getFollowingState(state, event) != null;
	}

	/**
	 * Check if the given region separates the two given states.
	 * @param utility The region utility to use.
	 * @param region The region to examine.
	 * @param state The first state of the separation problem
	 * @param otherState The second state of the separation problem
	 * @return A separating region or null.
	 */
	static public boolean isSeparatingRegion(RegionUtility utility, Region region, State state, State otherState) {
		List<Integer> stateParikhVector = utility.getReachingParikhVector(state);
		List<Integer> otherStateParikhVector = utility.getReachingParikhVector(otherState);

		// Unreachable states cannot be separated
		if (!utility.getSpanningTree().isReachable(state) || !utility.getSpanningTree().isReachable(otherState))
			return false;

		// We need a region which assigns different values to these two states.
		int stateValue = region.evaluateParikhVector(stateParikhVector);
		int otherStateValue = region.evaluateParikhVector(otherStateParikhVector);
		return stateValue != otherStateValue;
	}

	/**
	 * Check if the given region separates the state from the event.
	 * @param utility The region utility to use.
	 * @param region The region to examine.
	 * @param state The state of the separation problem
	 * @param event The event of the separation problem
	 * @return A separating region or null.
	 */
	static public boolean isSeparatingRegion(RegionUtility utility, Region region, State state, String event) {
		// Unreachable states cannot be separated
		if (!utility.getSpanningTree().isReachable(state))
			return false;

		// We need r(state) to be smaller than the event's backward weight in some region.
		return region.getMarkingForState(state) < region.getBackwardWeight(event);
	}

	/**
	 * Calculate a mapping from events to their location.
	 * @param utility The region utility that describes the events.
	 * @return An array containing the location for each event.
	 */
	static public String[] getLocationMap(RegionUtility utility) throws MissingLocationException {
		// Build a mapping from events to locations. Yaaay. Need to iterate over all arcs...
		String[] locationMap = new String[utility.getNumberOfEvents()];
		boolean hadEventWithLocation = false;

		for (Arc arc : utility.getTransitionSystem().getEdges()) {
			String location;
			try {
				location = arc.getExtension("location").toString();
			} catch (StructureException e) {
				// Because just returning "null" is too easy...
				continue;
			}

			int event = utility.getEventIndex(arc.getLabel());
			String oldLocation = locationMap[event];
			locationMap[event] = location;
			hadEventWithLocation = true;

			// The parser makes sure that this assertion always holds. If something constructs a PN which
			// breaks this assumption, then the bug is in that code.
			assert oldLocation == null || oldLocation.equals(location);
		}

		if (hadEventWithLocation) {
			// Do all events have a location?
			if (Arrays.asList(locationMap).contains(null))
				throw new MissingLocationException("Trying to synthesize a Petri Net where some events have a "
						+ "location and others do not. Either all or no event must have a location.");

			// Do all events have the same location?
			if (Collections.frequency(Arrays.asList(locationMap), locationMap[0]) == locationMap.length) {
				// No location handling needed, discard the map
				locationMap = new String[locationMap.length];
			}
		}

		return locationMap;
	}

	/**
	 * Construct a new Separation instance.
	 * @param utility The region utility to use.
	 * @param basis A basis of abstract regions of the underlying transition system. This collection must guarantee
	 * stable iteration order!
	 * @param properties Properties that the calculated region should satisfy.
	 * @return A suitable Separation instance
	 */
	static public Separation createSeparationInstance(RegionUtility utility, List<Region> basis,
			PNProperties properties) throws MissingLocationException {
		String[] locationMap = getLocationMap(utility);
		Separation result = null;
		try {
			if (result == null)
				result = new BasicPureSeparation(utility, basis, properties, locationMap);
		}
		catch (UnsupportedPNPropertiesException e) {
			// Ignore, try the other implementations
		}
		try {
			if (result == null)
				result = new BasicImpureSeparation(utility, basis, properties, locationMap);
		}
		catch (UnsupportedPNPropertiesException e) {
			// Ignore, try the other implementations
		}
		try {
			if (result == null)
				result = new PlainPureSeparation(utility, basis, properties, locationMap);
		}
		catch (UnsupportedPNPropertiesException e) {
			// Ignore, try the other implementations
		}
		if (result == null)
			result = new InequalitySystemSeparation(utility, basis, properties, locationMap);

		debug("Created Separation instance from class " + result.getClass().getName());
		return result;
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
