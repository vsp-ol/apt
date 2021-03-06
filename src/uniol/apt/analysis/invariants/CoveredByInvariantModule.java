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

package uniol.apt.analysis.invariants;

import java.util.Iterator;
import java.util.Set;
import uniol.apt.adt.pn.Node;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.module.AbstractModule;
import uniol.apt.module.Category;
import uniol.apt.module.ModuleInput;
import uniol.apt.module.ModuleInputSpec;
import uniol.apt.module.ModuleOutput;
import uniol.apt.module.ModuleOutputSpec;
import uniol.apt.module.exception.ModuleException;

/**
 * This module tests if a Petri net is covered by an S- or a T-invariant.
 * <p/>
 * @author Dennis Borde, Manuel Gieseking
 */
public class CoveredByInvariantModule extends AbstractModule {

	private final static String DESCRIPTION = "Check if a Petri net is covered by an S-invariant or a T-invariant";
	private final static String TITLE = "covered by invariant";
	private final static String NAME = "covered_by_invariant";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void require(ModuleInputSpec inputSpec) {
		inputSpec.addParameter("net", PetriNet.class, "The Petri net that should be examined");
		inputSpec.addParameter("inv", Character.class, "Parameter 's' for s-invariants "
			+ "and 't' for t-invariants.");
		inputSpec.addOptionalParameter("algo", Character.class, 'p', "Parameter 'f' for farkas algorithm and"
			+ " 'p' for the adapted farkas algorithm of pipe.");
	}

	@Override
	public void provide(ModuleOutputSpec outputSpec) {
		outputSpec.addReturnValue("covered by invariant", Boolean.class, ModuleOutputSpec.PROPERTY_SUCCESS);
		outputSpec.addReturnValue("mapping", String.class);
		outputSpec.addReturnValue("invariant", Vector.class);
	}

	@Override
	public void run(ModuleInput input, ModuleOutput output) throws ModuleException {
		PetriNet pn = input.getParameter("net", PetriNet.class);
		Character para = input.getParameter("inv", Character.class);
		Character algo = input.getParameter("algo", Character.class);
		InvariantCalculator.InvariantAlgorithm alg = (algo == 'f')
			? InvariantCalculator.InvariantAlgorithm.FARKAS : InvariantCalculator.InvariantAlgorithm.PIPE;
		Vector invariant;
		Set<? extends Node> nodes;
		if (para == 's') {
			invariant = InvariantCalculator.coveredBySInvariants(pn, alg);
			nodes = pn.getPlaces();
		} else if (para == 't') {
			invariant = InvariantCalculator.coveredByTInvariants(pn, alg);
			nodes = pn.getTransitions();
		} else {
			throw new ModuleException("Parameter for " + getName() + " has to be [s/t]");
		}
		output.setReturnValue("covered by invariant", Boolean.class, invariant != null);
		// Mapping
		StringBuilder sb = new StringBuilder("[");
		for (Iterator<? extends Node> it = nodes.iterator(); it.hasNext();) {
			Node node = it.next();
			sb.append(node.getId()).append("; ");
		}
		if (sb.length() - 2 >= 0) {
			sb.replace(sb.length() - 2, sb.length() - 1, "]");
		} else {
			sb.append("]");
		}
		output.setReturnValue("mapping", String.class, sb.toString());
		output.setReturnValue("invariant", Vector.class, invariant);
	}

	@Override
	public String getTitle() {
		return TITLE;
	}

	@Override
	public String getShortDescription() {
		return DESCRIPTION;
	}

	@Override
	public Category[] getCategories() {
		return new Category[]{Category.PN};
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
