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

package uniol.apt.io.converter;

import java.io.IOException;

import uniol.apt.adt.pn.PetriNet;
import uniol.apt.io.parser.impl.exception.FormatException;
import uniol.apt.io.parser.impl.pnml.PNMLParser;

import uniol.apt.module.AbstractModule;
import uniol.apt.module.Category;
import uniol.apt.module.ModuleInput;
import uniol.apt.module.ModuleInputSpec;
import uniol.apt.module.ModuleOutput;
import uniol.apt.module.ModuleOutputSpec;
import uniol.apt.module.exception.ModuleException;

/**
 * A Module for converting a file in PNML format to a file in apt format.
 *
 * @author Manuel Gieseking, Uli Schlachter
 */
public class PNML2AptModule extends AbstractModule {

	@Override
	public void require(ModuleInputSpec inputSpec) {
		inputSpec.addParameter("input_filename", String.class, "The file that should be converted.");
	}

	@Override
	public void provide(ModuleOutputSpec outputSpec) {
		outputSpec.addReturnValue("pn", PetriNet.class,
			ModuleOutputSpec.PROPERTY_FILE, ModuleOutputSpec.PROPERTY_RAW);
	}

	@Override
	public void run(ModuleInput input, ModuleOutput output) throws ModuleException {
		String filename = input.getParameter("input_filename", String.class);
		try {
			output.setReturnValue("pn", PetriNet.class, PNMLParser.getPetriNet(filename));
		} catch (IOException e) {
			throw new ModuleException("Cannot parse file '" + filename + "': File does not exist");
		} catch (FormatException ex) {
			throw new ModuleException(ex.getMessage(), ex);
		}
	}

	@Override
	public String getName() {
		return "pnml2apt";
	}

	@Override
	public String getTitle() {
		return "PNML2Apt";
	}

	@Override
	public String getShortDescription() {
		return "Convert PNML format to APT format";
	}

	@Override
	public Category[] getCategories() {
		return new Category[]{Category.CONVERTER};
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
