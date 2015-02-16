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

package uniol.apt.analysis.synthesize;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.FactoryUtils;
import org.apache.commons.collections4.map.LazyMap;

import uniol.apt.adt.exception.NoSuchNodeException;
import uniol.apt.adt.exception.NodeExistsException;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.adt.pn.Place;
import uniol.apt.adt.pn.Transition;
import uniol.apt.adt.ts.State;
import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.analysis.bounded.Bounded;
import uniol.apt.analysis.cf.ConflictFree;
import uniol.apt.analysis.coverability.CoverabilityGraph;
import uniol.apt.analysis.exception.PreconditionFailedException;
import uniol.apt.analysis.exception.UnboundedException;
import uniol.apt.analysis.isomorphism.IsomorphismLogic;
import uniol.apt.analysis.on.OutputNonBranching;
import uniol.apt.analysis.plain.Plain;
import uniol.apt.analysis.sideconditions.Pure;
import uniol.apt.analysis.synthesize.separation.Separation;
import uniol.apt.analysis.synthesize.separation.SeparationUtility;
import uniol.apt.analysis.tnet.TNet;
import uniol.apt.util.DebugUtil;
import uniol.apt.util.Pair;

/**
 * Synthesize a Petri Net from a transition system.
 * @author Uli Schlachter
 */
public class SynthesizePN extends DebugUtil {
	private final TransitionSystem ts;
	private final RegionBasis regionBasis;
	private final RegionUtility utility;
	private final Set<Region> regions = new HashSet<>();
	private final Set<Set<State>> maximalFailedStateSeparationProblems = new HashSet<>();
	private final Map<String, Set<State>> failedEventStateSeparationProblems = new HashMap<>();
	private final PNProperties properties;
	private final Separation separation;

	/**
	 * Synthesize a Petri Net which generates the given transition system.
	 * @param utility An instance of RegionUtility for the requested transition system.
	 * @param properties Properties that the synthesized Petri net should satisfy.
	 */
	public SynthesizePN(RegionUtility utility, PNProperties properties) throws MissingLocationException {
		this.ts = utility.getTransitionSystem();
		this.utility = utility;
		this.regionBasis = new RegionBasis(utility);
		this.properties = properties;
		this.separation = SeparationUtility.createSeparationInstance(utility, regionBasis, properties);

		debug("Region basis: ", regionBasis);

		// ESSP calculates new regions while SSP only choses regions from the basis. Solve ESSP first since the
		// calculated regions may also solve SSP and thus we get less places in the resulting net.
		debug();
		debug("Solving event-state separation");
		solveEventStateSeparation();

		debug();
		debug("Solving state separation");
		computeMaximalFailedStateSeparationProblems(solveStateSeparation());

		debug();
		debug("Minimizing regions");
		minimizeRegions(utility, regions);

		debug();
	}

	/**
	 * Synthesize a Petri Net which generates the given transition system.
	 * @param utility An instance of RegionUtility for the requested transition system.
	 */
	public SynthesizePN(RegionUtility utility) throws MissingLocationException {
		this(utility, new PNProperties());
	}

	/**
	 * Synthesize a Petri Net which generates the given transition system.
	 * @param ts The transition system to synthesize.
	 * @param properties Properties that the synthesized Petri net should satisfy.
	 */
	public SynthesizePN(TransitionSystem ts, PNProperties properties) throws MissingLocationException {
		this(new RegionUtility(ts), properties);
	}

	/**
	 * Synthesize a Petri Net which generates the given transition system.
	 * @param ts The transition system to synthesize.
	 */
	public SynthesizePN(TransitionSystem ts) throws MissingLocationException {
		this(ts, new PNProperties());
	}

	/**
	 * Calculate the set of states which aren't separated by the given regions.
	 */
	static public Set<State> calculateUnseparatedStates(RegionUtility utility, Set<Region> regions) {
		Set<State> result = new HashSet<>();
		Set<Set<State>> partition = new HashSet<>();
		partition.add(new HashSet<>(utility.getTransitionSystem().getNodes()));

		debug("Calculating unseparated states");
		for (Region region : regions) {
			int discarded = 0;
			Set<Set<State>> newPartition = new HashSet<>();
			for (Set<State> family : partition) {
				// Separate this family by the given region: States to which this region assigns
				// different markings are separated.
				Map<Integer, Set<State>> markings = LazyMap.lazyMap(new HashMap<Integer, Set<State>>(),
						FactoryUtils.prototypeFactory(new HashSet<State>()));
				for (State state : family) {
					try {
						markings.get(region.getMarkingForState(state)).add(state);
					}
					catch (UnreachableException e) {
						// Unreachable states cannot be separated, so add this to result
						result.add(state);
						continue;
					}
				}

				// Now collect families of not-yet-separated states
				for (Map.Entry<Integer, Set<State>> entry : markings.entrySet()) {
					if (entry.getValue().size() > 1)
						newPartition.add(entry.getValue());
					else
						discarded++;
				}
			}

			partition = newPartition;
			debug("After region ", region, ", still have ", partition.size(), " families (", discarded, " resulting singular families discarded)");
		}

		// All remaining states are not yet separated. Throw away the family information and return them all.
		for (Set<State> family : partition)
			result.addAll(family);

		return result;
	}

	/**
	 * Solve all instances of the state separation problem (SSP).
	 */
	private Set<Set<State>> solveStateSeparation() {
		Set<Set<State>> failedStateSeparationProblems = new HashSet<>();
		Set<State> remainingStates = new HashSet<>(calculateUnseparatedStates(utility, regions));
		Iterator<State> iterator = remainingStates.iterator();
		while (iterator.hasNext()) {
			State state = iterator.next();
			iterator.remove();

			for (State otherState : remainingStates) {
				debug("Trying to separate ", state,  " from ", otherState);
				Region r = null;
				for (Region region : regions)
					if (SeparationUtility.isSeparatingRegion(utility, region, state, otherState)) {
						r = region;
						break;
					}
				if (r != null) {
					debug("Found region ", r);
					continue;
				}

				r = separation.calculateSeparatingRegion(state, otherState);
				if (r == null) {
					Set<State> problem = new HashSet<>();
					problem.add(state);
					problem.add(otherState);
					failedStateSeparationProblems.add(problem);

					debug("Failure!");
				} else {
					debug("Calculated region ", r);
					regions.add(r);
				}
			}
		}
		return failedStateSeparationProblems;
	}

	/**
	 * Given a set of e.g. pairs of states, this computes maximal sets of states where each state in such a set
	 * cannot be separated from all the other states in the set. The basic observation for this is that if states
	 * {a,b} and {a,c} cannot be separated, then {b,c} cannot be separated either.
	 */
	private void computeMaximalFailedStateSeparationProblems(Set<Set<State>> failedStateSeparationProblems) {
		Map<State, Set<State>> stateToFailureGroup = new HashMap<>();
		for (Set<State> problem : failedStateSeparationProblems) {
			Set<State> problemGroup = new HashSet<>(problem);
			for (State state : problem) {
				Set<State> group = stateToFailureGroup.get(state);
				if (group != null)
					problemGroup.addAll(group);
			}
			for (State state : problem) {
				stateToFailureGroup.put(state, problemGroup);
			}
		}
		maximalFailedStateSeparationProblems.addAll(stateToFailureGroup.values());
	}

	/**
	 * Solve all instances of the event/state separation problem (ESSP).
	 */
	private void solveEventStateSeparation() {
		for (State state : ts.getNodes())
			for (String event : ts.getAlphabet()) {
				if (!SeparationUtility.isEventEnabled(state, event)) {
					debug("Trying to separate ", state, " from event '", event, "'");
					Region r = null;
					for (Region region : regions)
						if (SeparationUtility.isSeparatingRegion(utility, region, state, event)) {
							r = region;
							break;
						}
					if (r != null) {
						debug("Found region ", r);
						continue;
					}

					r = separation.calculateSeparatingRegion(state, event);
					if (r == null) {
						Set<State> states = failedEventStateSeparationProblems.get(event);
						if (states == null) {
							states = new HashSet<>();
							failedEventStateSeparationProblems.put(event, states);
						}
						states.add(state);
						debug("Failure!");
					} else {
						debug("Calculated region ", r);
						regions.add(r);
					}
				}
			}
	}

	/**
	 * Calculate definitely required regions and for each remaining separation problem all regions which solve this
	 * problem.
	 * @param utility The region utility on which this function should work.
	 * @param separationProblems For each still unsolved separation problem, the set of all regions that solve it is
	 * calculated and added to this argument.
	 * @param requiredRegions Regions which are definitely required will be added to this set.
	 * @param remainingRegions Regions to choose from and if they are definitely required to move to
	 * requiredRegions.
	 */
	static private void calculateRequiredRegionsAndProblems(RegionUtility utility, Set<Set<Region>> separationProblems,
			Set<Region> requiredRegions, Set<Region> remainingRegions) {
		TransitionSystem ts = utility.getTransitionSystem();
		// Event separation
		for (State state : ts.getNodes()) {
			innerStatesLoop:
			for (String event : ts.getAlphabet()) {
				if (!SeparationUtility.isEventEnabled(state, event)) {
					// Does one of our required regions already solve ESSP? If so, skip
					for (Region r : requiredRegions) {
						if (SeparationUtility.isSeparatingRegion(utility, r, state, event))
							continue innerStatesLoop;
					}
					// Calculate which of the remaining regions solves this ESSP instance
					Set<Region> sep = new HashSet<>();
					for (Region r : remainingRegions) {
						if (SeparationUtility.isSeparatingRegion(utility, r, state, event))
							sep.add(r);
					}
					if (sep.size() == 1) {
						// If only one region solves this problem, that region is required
						Region r = sep.iterator().next();
						requiredRegions.add(r);
						remainingRegions.remove(r);
					}
					else if (!sep.isEmpty())
						separationProblems.add(sep);
				}
			}
		}
		// State separation
		// All regions which are already separated by our requiredRegions can be skipped, so use
		// calculateUnseparatedStates() to look at the rest.
		Set<State> remainingStates = new HashSet<>(calculateUnseparatedStates(utility, requiredRegions));
		Iterator<State> iterator = remainingStates.iterator();
		while (iterator.hasNext()) {
			State state = iterator.next();
			iterator.remove();

			innerStatesLoop:
			for (State otherState : remainingStates) {
				// Does one of our required regions already solve SSP? If so, skip
				for (Region r : requiredRegions) {
					if (SeparationUtility.isSeparatingRegion(utility, r, state, otherState))
						continue innerStatesLoop;
				}
				// Calculate which of the remaining regions solves SSP for this instance
				Set<Region> sep = new HashSet<>();
				for (Region r : remainingRegions) {
					if (SeparationUtility.isSeparatingRegion(utility, r, state, otherState))
						sep.add(r);
				}
				if (sep.size() == 1) {
					// If only one region solves this problem, that region is required
					Region r = sep.iterator().next();
					requiredRegions.add(r);
					remainingRegions.remove(r);
				}
				else if (!sep.isEmpty())
					separationProblems.add(sep);
			}
		}
	}

	/**
	 * Try to eliminate redundant regions.
	 * @param utility The region utility on which this function should work.
	 * @param requiredRegions Set of regions to minimize. Redundant regions will be removed.
	 */
	static public void minimizeRegions(RegionUtility utility, Set<Region> requiredRegions) {
		TransitionSystem ts = utility.getTransitionSystem();
		Set<Region> allRegions = Collections.unmodifiableSet(new HashSet<>(requiredRegions));
		Set<Region> remainingRegions = new HashSet<>(requiredRegions);
		requiredRegions.clear();

		// Build a list where each entry is generated from a separation problem and contains all regions that
		// solve this problem.
		Set<Set<Region>> separationProblems = new HashSet<>();
		calculateRequiredRegionsAndProblems(utility, separationProblems, requiredRegions, remainingRegions);

		debug("Required regions after first pass:");
		debug(requiredRegions);
		debug("List of regions that solve each remaining separation problem:");
		debug(separationProblems);

		// Now go through all remaining problems again
		for (Set<Region> problem : separationProblems) {
			// If none of our required regions solve this problem, we pick one arbitrarily that does
			if (Collections.disjoint(requiredRegions, problem))
				requiredRegions.add(problem.iterator().next());
		}

		debug("List of required regions:");
		debug(requiredRegions);
		debug("Picked ", requiredRegions.size(), " required regions out of ", allRegions.size(), " input regions");
	}

	/**
	 * Get all separating regions which were calculated
	 * @return All separating regions found.
	 */
	public Set<Region> getSeparatingRegions() {
		return Collections.unmodifiableSet(regions);
	}

	/**
	 * Check if the transition system was successfully separated.
	 * @return True if the transition was successfully separated.
	 */
	public boolean wasSuccessfullySeparated() {
		return maximalFailedStateSeparationProblems.isEmpty() && failedEventStateSeparationProblems.isEmpty();
	}

	/**
	 * Get all the state separation problems which could not be solved.
	 * @return A set containing sets of two states which cannot be differentiated by any region.
	 */
	public Set<Set<State>> getFailedStateSeparationProblems() {
		return Collections.unmodifiableSet(maximalFailedStateSeparationProblems);
	}

	/**
	 * Get all the event/state separation problems which could not be solved.
	 * @return A set containing instances of the event/state separation problem.
	 */
	public Map<String, Set<State>> getFailedEventStateSeparationProblems() {
		// This would still allow modifying the entries of the map. Whatever...
		return Collections.unmodifiableMap(failedEventStateSeparationProblems);
	}

	public RegionUtility getUtility() {
		return utility;
	}

	static public boolean isDistributedImplementation(RegionUtility utility, PetriNet pn) {
		String[] locationMap;
		try {
			locationMap = SeparationUtility.getLocationMap(utility);
		}
		catch (MissingLocationException e) {
			debug("Couldn't get location map");
			return false;
		}

		// All transitions that consume tokens from the same place must have the same location.
		for (Place p : pn.getPlaces()) {
			String location = null;
			debug("Examining preset of place ", p, " for obeying the required distribution");
			for (Transition t : p.getPostset()) {
				int event = utility.getEventIndex(t.getLabel());
				if (locationMap[event] == null)
					continue;
				if (location == null) {
					location = locationMap[event];
					debug("Transition ", t, " sets location to ", location);
				} else if (!location.equals(locationMap[event])) {
					debug("Transition ", t, " would set location to ", locationMap[event], ", but this conflicts with earlier location");
					debug("PN is not a distributed implementation!");
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Synthesize a Petri Net from the separating regions that were calculated.
	 * @return The synthesized PetriNet
	 */
	public PetriNet synthesizePetriNet() {
		if (!wasSuccessfullySeparated())
			return null;
		PetriNet pn = synthesizePetriNet(utility, regions);

		// Test if the synthesized PN really satisfies all the properties that it should
		if (properties.isPure())
			assert Pure.checkPure(pn) : regions;
		if (properties.isPlain())
			assert new Plain().checkPlain(pn) : regions;
		if (properties.isTNet())
			try {
				assert new TNet(pn).testPlainTNet() : regions;
			}
			catch (PreconditionFailedException e) {
				assert false : regions;
			}
		if (properties.isKBounded())
			assert new Bounded().checkBounded(pn).k <= properties.getKForKBoundedness() : regions;
		if (properties.isOutputNonbranching())
			assert new OutputNonBranching(pn).check() : regions;
		if (properties.isConflictFree())
			try {
				assert new ConflictFree(pn).check() : regions;
			}
			catch (PreconditionFailedException e) {
				assert false : regions;
			}

		// The resulting PN should always have a reachability graph isomorphic to what we started with
		try {
			assert new IsomorphismLogic(new CoverabilityGraph(pn).toReachabilityLTS(), ts, true).isIsomorphic() : regions;
		}
		catch (UnboundedException e) {
			assert false : regions;
		}
		assert isDistributedImplementation(utility, pn) : regions;

		return pn;
	}

	/**
	 * Synthesize a Petri Net from the given regions.
	 * @param utility An instance of RegionUtility for the requested transition system.
	 * @param regions The regions that should be used for synthesis.
	 * @return The synthesized PetriNet
	 */
	public static PetriNet synthesizePetriNet(RegionUtility utility, Set<Region> regions) {
		PetriNet pn = new PetriNet();

		debug("Synthesizing PetriNet from these regions:");
		debug(regions);

		// First generate the transitions so that isolated transitions do get created
		for (String event : utility.getEventList())
			pn.createTransition(event);

		for (Region region : regions) {
			Place place = pn.createPlace();
			place.setInitialToken(region.getInitialMarking());
			place.putExtension(Region.class.getName(), region);

			for (String event : region.getRegionUtility().getEventList()) {
				Transition transition = pn.getTransition(event);
				int backward = region.getBackwardWeight(event);
				assert backward >= 0;
				if (backward > 0)
					pn.createFlow(place, transition, backward);

				int forward = region.getForwardWeight(event);
				assert forward >= 0;
				if (forward > 0)
					pn.createFlow(transition, place, forward);
			}
		}

		return pn;
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
