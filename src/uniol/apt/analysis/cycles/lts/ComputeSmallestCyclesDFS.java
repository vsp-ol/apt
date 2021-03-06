/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2012-2013  Members of the project group APT
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

package uniol.apt.analysis.cycles.lts;

import java.util.Collections;
import uniol.apt.adt.ts.ParikhVector;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import uniol.apt.adt.ts.Arc;
import uniol.apt.adt.ts.State;
import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.util.Pair;

/**
 * This class offers the possibility to compute the smallest cycles and parikh vectors of a transitionsystem with a
 * algorithm using the depth first search.
 * <p/>
 * @author Manuel Gieseking
 */
public class ComputeSmallestCyclesDFS {

	private TransitionSystem tsys;
	private Set<Pair<List<String>, ParikhVector>> cycles;

	/**
	 * Checks whether the given cycle is a smallest cycle and if it is so and another greater is in the list of the
	 * cycles, this one will be kicked.
	 * <p/>
	 * @param pair - the cycle to check.
	 * <p/>
	 * @return true if the cycle is a smallest cycle.
	 */
	private boolean isSmallestCycle(Pair<List<String>, ParikhVector> pair) {
		Pair<List<String>, ParikhVector> kick = null;
		for (Pair<List<String>, ParikhVector> pair2 : cycles) {
			if (pair2.getSecond().lessThan(pair.getSecond())) {
				return false;
			}
			if (pair.getSecond().lessThan(pair2.getSecond())) {
				kick = pair2;
				break;
			}
		}
		cycles.remove(kick);
		return true;
	}

	/**
	 * Computes the parikh vectors of all smallest cycles of a labeled transition system with a algorithm using the
	 * depth first search. (Requirement A10)
	 * <p/>
	 * @param ts       - the transitionsystem to examine.
	 * @param smallest - flag which tells if really all or just the smallest should be saved. (Storage vs. Time)
	 * <p/>
	 * @return a list of the smallest cycles of a given transitionsystem an their parikh vectors.
	 */
	public Set<Pair<List<String>, ParikhVector>> computePVsOfSmallestCycles(TransitionSystem ts, boolean smallest) {
		// compute cycles
		Set<Pair<List<String>, ParikhVector>> out = calculate(ts, smallest);
		if (!smallest) {
			// Compare smallest cycles.
			out = new HashSet<>();
			for (Pair<List<String>, ParikhVector> pair1 : cycles) {
				boolean lt = true;
				for (Pair<List<String>, ParikhVector> pair2 : cycles) {
					if (pair1 != pair2) {
						if (pair2.getSecond().lessThan(pair1.getSecond())) {
							lt = false;
							break;
						}
					}
				}
				if (lt) {
					out.add(pair1);
				}
			}
		}
		return out;
	}

	/**
	 * Calculates the smallest cycles and their parikh vectors. It is important to notice, that in most cases if
	 * abcd is an cycle, that also bcda, cdab, etc. with the same parikh vector are cycle but won't be saved
	 * additionaly. Just in cases that two different passes coming the cycles, than they will be saved.
	 * <p/>
	 * @param ts       - the transitionsystem to examine.
	 * @param smallest - flag which tells if really all or just the smallest should be saved. (Storage vs. Time)
	 * <p/>
	 * @return a set of cycles and the belonging parikh vectors.
	 */
	private Set<Pair<List<String>, ParikhVector>> calculate(TransitionSystem ts, boolean smallest) {
		// Reset results
		this.cycles = new HashSet<>();
		this.tsys = ts;
		if (ts.getNodes().isEmpty()) {
			return Collections.unmodifiableSet(cycles);
		}
		// Calls depth first search.
		dfs(ts.getInitialState(), new Stack<String>(), new Stack<String>(), smallest);

		return Collections.unmodifiableSet(cycles);
	}

	/**
	 * Depth first search function which is recursivly used to compute the smallest cycles.
	 * <p/>
	 * @param node     - the starting node
	 * @param sequence - a stack of all the visited nodes.
	 * @param labels   - a stack with the visited labels for the parikh vectors.
	 * @param smallest - flag which tells if really all or just the smallest should be saved. (Storage vs. Time)
	 */
	private void dfs(State node, Stack<String> sequence, Stack<String> labels, boolean smallest) {

		int idx = sequence.search(node.getId());
		if (idx != -1) {
			// Node does exist in stack. This means we have found a cycle and
			// can store it to our list of cycles.
			idx = sequence.size() - idx;
			List<String> cycle = new LinkedList<>(sequence.subList(idx, sequence.size()));
			List<String> cycleParikh = new LinkedList<>(labels.subList(idx, sequence.size()));
			Pair<List<String>, ParikhVector> pair = new Pair<>(cycle, new ParikhVector(tsys, cycleParikh));
			if (smallest) {
				if (isSmallestCycle(pair)) {
					cycles.add(pair);
				}
			} else {
				cycles.add(pair);
			}
			return;
		}

		// The node does not exist in stack. Push it.
		sequence.push(node.getId());

		// Iterate through all postsetedges.
		for (Arc neigh : node.getPostsetEdges()) {
			labels.push(neigh.getLabel()); // Labels stack for parikh vector generation.
			// Recursive call of dfs.
			dfs(neigh.getTarget(), sequence, labels, smallest);
			labels.pop(); // Labels stack for parikh vector generation.
		}

		// Remove node from stack.
		sequence.pop();
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
