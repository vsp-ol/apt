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

package uniol.apt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import uniol.apt.adt.pn.PetriNet;
import uniol.apt.io.parser.PNParser;
import uniol.apt.io.parser.ParseException;
import uniol.apt.io.parser.impl.AptPNParser;
import uniol.apt.io.parser.impl.LoLAPNParser;
import uniol.apt.io.parser.impl.SynetPNParser;
import uniol.apt.io.renderer.PNRenderer;
import uniol.apt.io.renderer.RenderException;
import uniol.apt.io.renderer.impl.APTRenderer;
import uniol.apt.io.renderer.impl.BagginsRenderer;
import uniol.apt.io.renderer.impl.DotPNRenderer;
import uniol.apt.io.renderer.impl.LoLARenderer;
import uniol.apt.io.renderer.impl.PetrifyRenderer;
import uniol.apt.io.renderer.impl.SynetRenderer;

/**
 * Utility for conversion between arbitrary (supported) Petri net formats
 *
 * @author vsp
 */
public class PNConvert {
	private class ArgException extends Exception {
		public static final long serialVersionUID = 0x1l;

		private ArgException(String message) {
			super(message);
		}
	}

	private static final Map<String, PNParser>   PARSERS;
	private static final Map<String, PNRenderer> RENDERERS;

	private static final Options OPTIONS;

	// initialise map of parsers
	static {
		Map<String, PNParser> parserMap = new HashMap<>();
		parserMap.put("apt", new AptPNParser());
		parserMap.put("lola", new LoLAPNParser());
		parserMap.put("synet", new SynetPNParser());
		PARSERS = Collections.unmodifiableMap(parserMap);
	}

	// initialise map of renderers
	static {
		Map<String, PNRenderer> rendererMap = new HashMap<>();
		rendererMap.put("apt", new APTRenderer());
		rendererMap.put("baggins", new BagginsRenderer());
		rendererMap.put("dot", new DotPNRenderer());
		rendererMap.put("lola", new LoLARenderer());
		rendererMap.put("petrify", new PetrifyRenderer());
		rendererMap.put("synet", new SynetRenderer());
		RENDERERS = Collections.unmodifiableMap(rendererMap);
	}

	// initialise options
	static {
		OPTIONS = new Options();
		Option  inputFileOpt    = Option.builder("i")
				.longOpt("input")
				.hasArg()
				.argName("input")
				.desc("Read input net from specified file.")
				.build();
		Option  inputFormatOpt  = Option.builder("I")
				.longOpt("inputformat")
				.hasArg()
				.argName("inputformat")
				.desc("Input net format.")
				.required()
				.build();
		Option  outputFileOpt   = Option.builder("o")
				.longOpt("output")
				.hasArg()
				.argName("output")
				.desc("Write output net into specified file.")
				.build();
		Option  outputFormatOpt = Option.builder("O")
				.longOpt("outputformat")
				.hasArg()
				.argName("outputformat")
				.desc("Output net format.")
				.required()
				.build();
		Option  forceOpt        = new Option("f", "force", false, "Don't fail if output file exists.");

		OPTIONS.addOption(inputFileOpt);
		OPTIONS.addOption(inputFormatOpt);
		OPTIONS.addOption(outputFileOpt);
		OPTIONS.addOption(outputFormatOpt);
		OPTIONS.addOption(forceOpt);
	}

	// print usage message
	private static void usage() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("java -cp apt.jar uniol.apt.PNConvert", OPTIONS);
	}

	private PNConvert() { /* hide constructor */ }

	/**
	 * main method
	 *
	 * @param args arguments from the command line
	 */
	public static void main(String[] args) {
		PNConvert conv = new PNConvert();
		try {
			conv.parseArgs(args);
			conv.run();
		} catch (org.apache.commons.cli.ParseException ex) {
			System.err.println("Error: " + ex.getMessage());
			System.err.println();
			usage();
		} catch (ArgException | IOException | ParseException | RenderException ex) {
			System.err.println("Error: " + ex.getMessage());
		}
	}

	private String inputFile;
	private String outputFile;

	private InputStream getInputStream() throws IOException {
		if (this.inputFile != null) {
			return FileUtils.openInputStream(new File(this.inputFile));
		} else {
			return System.in;
		}
	}

	private OutputStream getOutputStream() throws IOException {
		if (this.outputFile != null) {
			return FileUtils.openOutputStream(new File(this.outputFile));
		} else {
			return System.out;
		}
	}

	private PNParser     parser;
	private PNRenderer   renderer;

	// parser arguments
	private void parseArgs(String args[]) throws org.apache.commons.cli.ParseException, ArgException, IOException {
		CommandLine cmd = new DefaultParser().parse(OPTIONS, args);

		String inputFormat  = cmd.getOptionValue("inputformat");
		String outputFormat = cmd.getOptionValue("outputformat");
		this.inputFile      = cmd.getOptionValue("input");
		this.outputFile     = cmd.getOptionValue("output");
		boolean force       = cmd.hasOption("force");

		if (this.inputFile != null && !(new File(this.inputFile)).exists()) {
			throw new ArgException("input file doesn't exist.");
		}

		if (this.outputFile != null && !force && new File(this.outputFile).exists()) {
			throw new ArgException("output file exists and force isn't set");
		}

		this.parser   = PARSERS.get(inputFormat.toLowerCase());
		if (this.parser == null) {
			throw new ArgException("Unknown input format '" + inputFormat + "'");
		}

		this.renderer = RENDERERS.get(outputFormat.toLowerCase());
		if (this.renderer == null) {
			throw new ArgException("Unknown output format '" + outputFormat + "'");
		}
	}

	// run the conversion
	private void run() throws ParseException, RenderException, IOException {
		PetriNet pn   = this.parser.parsePN(getInputStream());
		String output = this.renderer.render(pn);
		IOUtils.write(output, getOutputStream());
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
