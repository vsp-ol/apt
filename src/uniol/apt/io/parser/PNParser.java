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

package uniol.apt.io.parser;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import uniol.apt.adt.pn.PetriNet;

/**
 * Interface for Petri net parsers.
 *
 * @author vsp
 */
public interface PNParser {
	/**
	 * Parse a Petri net
	 *
	 * @param input The input string to read the Petri net from
	 * @returns {@link PetriNet} which got read from the input
	 * @throws ParseException If the input can't get parsed
	 */
	public PetriNet parsePN(String input) throws ParseException;

	/**
	 * Parse a Petri net
	 *
	 * @param input The input stream to read the Petri net from
	 * @returns {@link PetriNet} which got read from the input
	 * @throws ParseException If the input can't get parsed
	 */
	public PetriNet parsePN(InputStream input) throws ParseException;

	/**
	 * Parse a Petri net from file
	 *
	 * @param filename The input filename to read the Petri net from
	 * @returns {@link PetriNet} which got read from the input
	 * @throws ParseException If the input can't get parsed
	 */
	public PetriNet parsePNFile(String filename) throws ParseException, IOException;

	/**
	 * Parse a Petri net from file
	 *
	 * @param filename The input filename to read the Petri net from
	 * @returns {@link PetriNet} which got read from the input
	 * @throws ParseException If the input can't get parsed
	 */
	public PetriNet parsePNFile(File file) throws ParseException, IOException;
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
