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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uniol.apt.adt.ts.Arc;
import uniol.apt.adt.ts.State;
import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.util.SpanningTree;
import uniol.apt.util.equations.EquationSystem;

/**
 * Utility class for generating regions. This class assigns indices to the variable and produces parikh vectors that
 * reach a given state from the initial state.
 * @author Uli Schlachter
 */
public class RegionUtility {
	private final TransitionSystem ts;
	private final SpanningTree<TransitionSystem, Arc, State> tree;
	private final List<String> eventList;
	private final Map<State, List<Integer>> parikhVectorMap = new HashMap<>();
	private List<Region> regionBasis;

	/**
	 * Construct a new RegionUtility.
	 * @param tree A spanning tree for the given transition system.
	 */
	public RegionUtility(SpanningTree<TransitionSystem, Arc, State> tree) {
		this.ts = tree.getGraph();
		this.tree = tree;
		this.eventList = Collections.unmodifiableList(new ArrayList<>(ts.getAlphabet()));
		this.regionBasis = null;
	}

	/**
	 * Construct a new RegionUtility.
	 * @param ts The TransitionSystem on which regions should be examined.
	 */
	public RegionUtility(TransitionSystem ts) {
		this(new SpanningTree<TransitionSystem, Arc, State>(ts, ts.getInitialState()));
	}

	/**
	 * Get the index of the given event.
	 * @param event The event whose index should be returned.
	 * @return The event's index or -1.
	 */
	public int getEventIndex(String event) {
		return eventList.indexOf(event);
	}

	/**
	 * Get the list of events of the underlying transition system. This list is ordered to form vectors. So the
	 * first entry of a vector corresponds to the first event in this list etc.
	 * @return The event list
	 */
	public List<String> getEventList() {
		return eventList;
	}

	/**
	 * Get the number of events.
	 * @return The number of events.
	 */
	public int getNumberOfEvents() {
		return eventList.size();
	}

	/**
	 * Get the spanning tree on which this RegionUtility operates.
	 * @return The spanning tree
	 */
	public SpanningTree<TransitionSystem, Arc, State> getSpanningTree() {
		return tree;
	}

	/**
	 * Get the transition system on which this RegionUtility operatores.
	 * @return The transition system
	 */
	public TransitionSystem getTransitionSystem() {
		return tree.getGraph();
	}

	/**
	 * @param node The node whose Parikh vector should be returned.
	 * @return The Parikh vector that reaches the node from the initial state.
	 * @throws UnreachableException if the given state is unreachable from the initial state
	 */
	public List<Integer> getReachingParikhVector(State node) throws UnreachableException {
		List<Integer> result = parikhVectorMap.get(node);
		if (result == null) {
			if (node.equals(tree.getStartNode())) {
				Integer[] array = new Integer[eventList.size()];
				Arrays.fill(array, 0);
				result = Arrays.asList(array);
			} else {
				Arc predecessor = tree.getPredecessorEdge(node);
				if (predecessor == null)
					throw new UnreachableException(ts, node);

				List<Integer> predVector = getReachingParikhVector(predecessor.getSource());
				result = new ArrayList<>();
				result.addAll(predVector);

				int idx = getEventIndex(predecessor.getLabel());
				result.set(idx, result.get(idx) + 1);
			}
			result = Collections.unmodifiableList(result);
			parikhVectorMap.put(node, result);
		}
		return result;
	}

	/**
	 * Get the Parikh vector for an edge. This Parikh vector is Psi_t = Psi_s + e_i - Psi_{s'} for an edge
	 * t = s--[a_i]->s'.
	 * @param edge The edge to examine.
	 * @return The edge's Parikh vector or an empty list if none exists.
	 * @throws UnreachableException if the given state is unreachable from the initial state
	 */
	public List<Integer> getParikhVectorForEdge(Arc edge) throws UnreachableException {
		State source = edge.getSource();
		State target = edge.getTarget();
		List<Integer> sourcePV = getReachingParikhVector(source);
		List<Integer> targetPV = getReachingParikhVector(target);
		int eventIndex = getEventIndex(edge.getLabel());

		if (sourcePV.isEmpty() || targetPV.isEmpty())
			return Collections.emptyList();

		List<Integer> result = new ArrayList<>(eventList.size());
		for (int i = 0; i < eventList.size(); i++)
			result.add(sourcePV.get(i) - targetPV.get(i) + (i == eventIndex ? 1 : 0));

		return Collections.unmodifiableList(result);
	}

	/**
	 * Calculate a basis of abstract regions for the LTS that this region utility belongs to.
	 * This calculates a basis of abstract regions via proposition 6.14 from "Petri Net Synthesis" by Badouel,
	 * Bernardinello and Darondeau. All regions on the LTS are a linear combinations of the elements in the basis.
	 * @return The region basis.
	 */
	public List<Region> getRegionBasis() {
		if (this.regionBasis == null) {
			EquationSystem system = new EquationSystem(this.getNumberOfEvents());

			// The events on each fundamental circle must form a T-Invariant of a Petri Net which generates
			// this transition system. Thus, each region must have zero effect on such a circle.
			for (Arc chord : tree.getChords()) {
				try {
					system.addEquation(this.getParikhVectorForEdge(chord));
				} catch (UnreachableException e) {
					throw new AssertionError("A chord by definition belongs to reachable nodes, "
							+ "yet one of them was unreachable?", e);
				}
			}

			List<Region> result = new ArrayList<>();
			for (List<Integer> vector : system.findBasis())
				result.add(Region.createPureRegionFromVector(this, vector));

			this.regionBasis = Collections.unmodifiableList(result);
		}
		return this.regionBasis;
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
