/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2015  vsp
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

package uniol.apt.io.renderer.impl;

import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import uniol.apt.adt.pn.PetriNet;
import uniol.apt.io.renderer.PNRenderer;

/**
 * This class renders Petri nets in the dot graph file format
 * @author vsp
 */
public class DotPNRenderer implements PNRenderer {
	@Override
	public String render(PetriNet pn) {
		STGroup group = new STGroupFile("uniol/apt/io/renderer/impl/Dot.stg");
		ST pnTemplate = group.getInstanceOf("pn");

		pnTemplate.add("places", pn.getPlaces());
		pnTemplate.add("transitions", pn.getTransitions());
		pnTemplate.add("arcs", pn.getEdges());

		return pnTemplate.render();
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
