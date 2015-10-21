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

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import uniol.apt.adt.pn.Marking;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.io.parser.PNParser;
import uniol.apt.io.parser.ParseException;


/**
 * LoLA llnet format parser
 *
 * @author vsp
 */
public class LoLAPNParser extends AbstractPNParser implements PNParser {
	private static class LoLAPNListener extends LoLAPNFormatBaseListener implements LoLAPNFormatListener {
		private PetriNet pn;
		private MarkingHashMap pws;
		private String curTransition;

		private PetriNet getPN() {
			return pn;
		}

		@Override
		public void enterPn(LoLAPNFormatParser.PnContext ctx) {
			this.pn = new PetriNet();
		}

		@Override
		public void exitPlaces(LoLAPNFormatParser.PlacesContext ctx) {
			assert this.pn != null;
			for (TerminalNode n : ctx.ID()) {
				this.pn.createPlace(n.getText());
			}
		}

		@Override
		public void enterPwList(LoLAPNFormatParser.PwListContext ctx) {
			this.pws = new MarkingHashMap();
		}

		@Override
		public void exitPw(LoLAPNFormatParser.PwContext ctx) {
			assert this.pws != null;
			this.pws.put(ctx.ID().getText(), Integer.parseInt(ctx.INT().getText()));
		}

		@Override
		public void exitMarking(LoLAPNFormatParser.MarkingContext ctx) {
			assert this.pn != null;
			assert this.pws != null;
			this.pn.setInitialMarking(new Marking(this.pn, this.pws));
			this.pws = null;
		}

		@Override
		public void enterTransition(LoLAPNFormatParser.TransitionContext ctx) {
			assert this.pn != null;
			this.curTransition = ctx.ID().getText();
			this.pn.createTransition(this.curTransition);
		}

		@Override
		public void exitTransition(LoLAPNFormatParser.TransitionContext ctx) {
			this.curTransition = null;
		}

		@Override
		public void exitTransitionPreset(LoLAPNFormatParser.TransitionPresetContext ctx) {
			assert this.pn != null;
			assert this.curTransition != null;
			assert this.pws != null;
			for (Map.Entry<String, Integer> entry : this.pws.entrySet()) {
				this.pn.createFlow(entry.getKey(), this.curTransition, entry.getValue());
			}
			this.pws = null;
		}

		@Override
		public void exitTransitionPostset(LoLAPNFormatParser.TransitionPostsetContext ctx) {
			assert this.pn != null;
			assert this.curTransition != null;
			assert this.pws != null;
			for (Map.Entry<String, Integer> entry : this.pws.entrySet()) {
				this.pn.createFlow(this.curTransition, entry.getKey(), entry.getValue());
			}
			this.pws = null;
		}
	}

	@Override
	public PetriNet parsePN(InputStream is) throws ParseException {
		CharStream input;
		try {
			input             = new ANTLRInputStream(is);
		} catch (IOException ex) {
			throw new ParseException(ex.getMessage(), ex);
		}
		LoLAPNFormatLexer lexer   = new LoLAPNFormatLexer(input);
		lexer.removeErrorListeners(); // don't spam on stderr
		lexer.addErrorListener(new ThrowingErrorListener());
		CommonTokenStream tokens  = new CommonTokenStream(lexer);
		LoLAPNFormatParser parser = new LoLAPNFormatParser(tokens);
		parser.removeErrorListeners(); // don't spam on stderr
		parser.addErrorListener(new ThrowingErrorListener());
		parser.setBuildParseTree(true);
		ParseTree tree;
		try {
			tree             = parser.pn();
		} catch (ParseCancellationException ex) {
			throw new ParseException(ex.getMessage(), ex);
		}
		ParseTreeWalker walker    = new ParseTreeWalker();
		LoLAPNListener listener   = new LoLAPNListener();
		walker.walk(listener, tree);

		return listener.getPN();
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
