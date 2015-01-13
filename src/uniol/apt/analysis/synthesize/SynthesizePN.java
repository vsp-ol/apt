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
import java.util.HashSet;
import java.util.Set;

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
import uniol.apt.util.Pair;

/**
 * Synthesize a Petri Net from a transition system.
 * @author Uli Schlachter
 */
public class SynthesizePN {
	private final TransitionSystem ts;
	private final RegionBasis regionBasis;
	private final RegionUtility utility;
	private final Set<Region> regions = new HashSet<>();
	private final Set<Set<State>> failedStateSeparationProblems = new HashSet<>();
	private final Set<Pair<String, State>> failedEventStateSeparationProblems = new HashSet<>();
	private final PNProperties properties;
	private final Separation separation;

	private static void debug(String message) {
		//System.err.println("SynthesizePN: " + message);
	}

	private static void debug() {
		debug("");
	}

	private static void debug(Object obj) {
		debug(obj.toString());
	}

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

		debug("Region basis: " + regionBasis);

		// ESSP calculates new regions while SSP only choses regions from the basis. Solve ESSP first since the
		// calculated regions may also solve SSP and thus we get less places in the resulting net.
		debug();
		debug("Solving event-state separation");
		solveEventStateSeparation();

		debug();
		debug("Solving state separation");
		solveStateSeparation();

		debug();
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
	 * Solve all instances of the state separation problem (SSP).
	 */
	private void solveStateSeparation() {
		Set<State> alreadyHandled = new HashSet<>();
		for (State state : ts.getNodes()) {
			alreadyHandled.add(state);
			for (State otherState : ts.getNodes()) {
				if (alreadyHandled.contains(otherState))
					continue;

				debug("Trying to separate " + state + " from " + otherState);
				Region r = null;
				for (Region region : regions)
					if (SeparationUtility.isSeparatingRegion(utility, region, state, otherState)) {
						r = region;
						break;
					}
				if (r != null) {
					debug("Found region " + r);
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
					debug("Calculated region " + r);
					regions.add(r);
				}
			}
		}
	}

	/**
	 * Solve all instances of the event/state separation problem (ESSP).
	 */
	private void solveEventStateSeparation() {
		for (State state : ts.getNodes())
			for (String event : ts.getAlphabet()) {
				if (!SeparationUtility.isEventEnabled(state, event)) {
					debug("Trying to separate " + state + " from event '" + event + "'");
					Region r = null;
					for (Region region : regions)
						if (SeparationUtility.isSeparatingRegion(utility, region, state, event)) {
							r = region;
							break;
						}
					if (r != null) {
						debug("Found region " + r);
						continue;
					}

					r = separation.calculateSeparatingRegion(state, event);
					if (r == null) {
						failedEventStateSeparationProblems.add(
								new Pair<>(event, state));
						debug("Failure!");
					} else {
						debug("Calculated region " + r);
						regions.add(r);
					}
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
		Set<Region> allRegions = new HashSet<>(requiredRegions);
		requiredRegions.clear();

		// Build a list where each entry is generated from a separation problem and contains all regions that
		// solve this problem.
		Set<Set<Region>> separationProblems = new HashSet<>();

		// State separation
		Set<State> alreadyHandled = new HashSet<>();
		for (State state : ts.getNodes()) {
			alreadyHandled.add(state);
			for (State otherState : ts.getNodes()) {
				if (alreadyHandled.contains(otherState))
					continue;

				Set<Region> sep = new HashSet<>();
				for (Region r : allRegions) {
					if (SeparationUtility.isSeparatingRegion(utility, r, state, otherState))
						sep.add(r);
				}
				if (sep.size() == 1)
					// If only one region solves this problem, that region is required
					requiredRegions.add(sep.iterator().next());
				else if (!sep.isEmpty())
					separationProblems.add(sep);
			}
		}
		// Event separation
		for (State state : ts.getNodes()) {
			for (String event : ts.getAlphabet()) {
				if (!SeparationUtility.isEventEnabled(state, event)) {
					Set<Region> sep = new HashSet<>();
					for (Region r : allRegions) {
						if (SeparationUtility.isSeparatingRegion(utility, r, state, event))
							sep.add(r);
					}
					if (sep.size() == 1)
						// If only one region solves this problem, that region is required
						requiredRegions.add(sep.iterator().next());
					else if (!sep.isEmpty())
						separationProblems.add(sep);
				}
			}
		}

		debug("List of regions that solve each separation problem:");
		debug(separationProblems);
		debug("required regions after first pass:");
		debug(requiredRegions);

		// Now go through all remaining problems again
		for (Set<Region> problem : separationProblems) {
			// If none of our required regions solve this problem, we pick one arbitrarily that does
			if (Collections.disjoint(requiredRegions, problem))
				requiredRegions.add(problem.iterator().next());
		}

		debug("List of required regions:");
		debug(requiredRegions);
		debug("Picked " + requiredRegions.size() + " required regions out of " + allRegions.size() + " input regions");
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
		return failedStateSeparationProblems.isEmpty() && failedEventStateSeparationProblems.isEmpty();
	}

	/**
	 * Get all the state separation problems which could not be solved.
	 * @return A set containing sets of two states which cannot be differentiated by any region.
	 */
	public Set<Set<State>> getFailedStateSeparationProblems() {
		return Collections.unmodifiableSet(failedStateSeparationProblems);
	}

	/**
	 * Get all the event/state separation problems which could not be solved.
	 * @return A set containing instances of the event/state separation problem.
	 */
	public Set<Pair<String, State>> getFailedEventStateSeparationProblems() {
		return Collections.unmodifiableSet(failedEventStateSeparationProblems);
	}

	public RegionUtility getUtility() {
		return utility;
	}

	/**
	 * Synthesize a Petri Net from the separating regions that were calculated.
	 * @return The synthesized PetriNet
	 */
	public PetriNet synthesizePetriNet() {
		if (!wasSuccessfullySeparated())
			return null;
		PetriNet pn = synthesizePetriNet(regions);

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
		return pn;
	}

	/**
	 * Synthesize a Petri Net from the given regions.
	 * @param regions The regions that should be used for synthesis.
	 * @return The synthesized PetriNet
	 */
	public static PetriNet synthesizePetriNet(Set<Region> regions) {
		PetriNet pn = new PetriNet();

		debug("Synthesizing PetriNet from these regions:");
		debug(regions);

		for (Region region : regions) {
			Place place = pn.createPlace();
			place.setInitialToken(region.getInitialMarking());
			place.putExtension(Region.class.getName(), region);

			for (String event : region.getRegionUtility().getEventList()) {
				Transition transition = getOrCreateTransition(pn, event);
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

	/**
	 * Get or create the given transition in the Petri Net.
	 */
	private static Transition getOrCreateTransition(PetriNet pn, String id) {
		try {
			return pn.getTransition(id);
		} catch (NoSuchNodeException ex) {
			try {
				return pn.createTransition(id);
			} catch (NodeExistsException ex2) {
				throw new AssertionError("Tried to get or create transition with id '" + id + "'. " +
						"Getting failed, claiming that the transition doesn't exist. " +
						"Creating failed, claiming that the transition already exists.", ex2);
			}
		}
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
