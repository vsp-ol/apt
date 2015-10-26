/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2014  vsp
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

package uniol.apt.io.parser.impl;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.io.parser.ParseException;
import uniol.apt.io.parser.LTSParser;

/**
 * Abstract base class for labeled transition system parsers.
 *
 * @author vsp
 */
public abstract class AbstractLTSParser implements LTSParser {
	@Override
	public TransitionSystem parseLTS(String input) throws ParseException {
		return parseLTS(IOUtils.toInputStream(input));
	}

	@Override
	public TransitionSystem parseLTSFile(String filename) throws ParseException, IOException {
		return parseLTSFile(new File(filename));
	}

	@Override
	public TransitionSystem parseLTSFile(File file) throws ParseException, IOException {
		return parseLTS(FileUtils.openInputStream(file));
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
