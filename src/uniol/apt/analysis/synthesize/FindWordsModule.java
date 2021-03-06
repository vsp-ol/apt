/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2014-2015  Uli Schlachter
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import java.io.PrintStream;

import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.analysis.exception.NonDeterministicException;
import uniol.apt.module.AbstractModule;
import uniol.apt.module.Category;
import uniol.apt.module.ModuleInput;
import uniol.apt.module.ModuleInputSpec;
import uniol.apt.module.ModuleOutput;
import uniol.apt.module.ModuleOutputSpec;
import uniol.apt.module.exception.ModuleException;

/**
 * Find all words that can be generated by a Petri net from a given class.
 * @author Uli Schlachter
 */
public class FindWordsModule extends AbstractModule {
	static public enum Operation {
		UNSOLVABLE(true, true, false),
		SOLVABLE(true, false, true),
		QUIET(false, false, false);

		private final boolean status;
		private final boolean unsolvable;
		private final boolean solvable;

		Operation(boolean status, boolean unsolvable, boolean solvable) {
			this.status = status;
			this.unsolvable = unsolvable;
			this.solvable = solvable;
		}

		public boolean printStatus() {
			return status;
		}

		public boolean printUnsolvable() {
			return unsolvable;
		}

		public boolean printSolvable() {
			return solvable;
		}
	}

	@Override
	public String getShortDescription() {
		return "Print either minimal unsolvable or all solvable words of some class";
	}

	@Override
	public String getLongDescription() {
		return getShortDescription() + ".\n\n"
			+ "This module only prints a subset of all words. For this, an equivalence relation on words "
			+ "is used were two words are equivalent if one can be created from the other by replacing "
			+ "letters with other letters. For example, 'abc' and 'abd' are equivalent in this sense, since "
			+ "c->d turns one into the other.\n"
			+ "More concretely, words are generated so that the last letter of the word is the first letter "
			+ "of the alphabet. Then, the next new letter from the end is the second letter of the alphabet, "
			+ "and so on.\n"
			+ "\nExample calls:\n\n"
			+ " apt " + getName() + " safe solvable abc: Print all words solvable by safe Petri nets over the alphabet {a,b,c}\n"
			+ " apt " + getName() + " none unsolvable ab: Print all minimally unsolvable words over the alphabet {a,b}\n";
	}

	@Override
	public String getName() {
		return "find_words";
	}

	@Override
	public void require(ModuleInputSpec inputSpec) {
		inputSpec.addParameter("options", String.class, "options");
		inputSpec.addParameter("operation", String.class, "Choose between printing all minimal 'unsolvable' words or all 'solvable' words");
		inputSpec.addParameter("alphabet", String.class, "Letters that should be part of the alphabet");
	}

	@Override
	public void provide(ModuleOutputSpec outputSpec) {
		// This module prints to System.out.
	}

	@Override
	public void run(ModuleInput input, ModuleOutput output) throws ModuleException {
		String optionsStr = input.getParameter("options", String.class);
		String alphabetLetter = input.getParameter("alphabet", String.class);
		String operation = input.getParameter("operation", String.class);

		PNProperties properties = SynthesizeModule.Options.parseProperties(optionsStr).properties;
		SortedSet<String> alphabet = new TreeSet<>(Arrays.asList(alphabetLetter.split("")));

		switch (operation) {
			case "unsolvable":
				generateList(properties, alphabet, Operation.UNSOLVABLE);
				break;
			case "solvable":
				generateList(properties, alphabet, Operation.SOLVABLE);
				break;
			default:
				throw new ModuleException("Unknown operation '" + operation + "', valid options are 'unsolvable' and 'solvable'");
		}
	}

	static public void generateList(PNProperties properties, SortedSet<String> alphabet, Operation operation) {
		Collection<List<String>> currentLevel = Collections.singleton(Collections.<String>emptyList());
		Collection<List<String>> nextLevel = new LinkedHashSet<>();

		boolean printSolvable = operation.printSolvable();
		boolean printUnsolvable = operation.printUnsolvable();
		if (operation.printStatus()) {
			String print;
			if (printSolvable && printUnsolvable)
				print = "solvable and minimal unsolvable";
			else if (printSolvable)
				print = "solvable";
			else if (printUnsolvable)
				print = "unsolvable";
			else
				print = "no";
			System.out.println("Looking for " + print + " words from class " + properties.toString() + " over the alphabet " + alphabet);
		}

		while (!currentLevel.isEmpty()) {
			int numUnsolvable = 0;
			int currentLength = currentLevel.iterator().next().size() + 1;
			for (List<String> currentWord : currentLevel) {
				for (String c : alphabet) {
					boolean newLetter = !currentWord.contains(c);

					// If "currentWord" is unsolvable, then "word" must also be unsolvable.
					// Otherwise we get a contradiction: The net solving "word" will solve
					// "currentWord" after firing "c" once.
					// Put differently: By prepending letters to solvable words, we are sure to
					// generate all solvable words.
					List<String> word = new ArrayList<>();
					word.add(c);
					word.addAll(currentWord);

					if (!properties.isKBounded()) {
						// If we have unbounded places, then every prefix of a solvable word is
						// also solvable: Just add a place from which every transition consumes
						// one token. This place's initial marking limits the length of the
						// word.
						// For our purpose this means: If the prefix isn't solvable, then we
						// already know that the word itself isn't solvable either.
						// This is also important for the definition of "minimally unsolvable".
						if (!currentLevel.contains(normalizeWord(word.subList(0, word.size() - 1), alphabet)))
							continue;
					}

					// Is "word" PN-solvable with the given properties?
					TransitionSystem ts = SynthesizeWordModule.makeTS(word);
					SynthesizePN synthesize;
					try {
						synthesize = new SynthesizePN.Builder(ts)
							.setProperties(properties)
							.setQuickFail(true)
							.buildForLanguageEquivalence();
					} catch (MissingLocationException e) {
						throw new RuntimeException("Not generating locations and "
								+ " yet they were generated wrongly?!", e);
					} catch (NonDeterministicException e) {
						throw new RuntimeException("Generated a deterministic TS and "
								+ " yet it is non-deterministic?!", e);
					}
					if (synthesize.wasSuccessfullySeparated()) {
						nextLevel.add(word);
						if (printSolvable)
							printWord(System.out, word);
					} else {
						numUnsolvable++;
						if (printUnsolvable)
							printWord(System.out, word);
					}

					if (newLetter)
						// The alphabet is a sorted set. We only extend words in the order that
						// they appear in the alphabet. So if the current letter was new, then
						// all the following ones will be new, too.
						// When extending "ba", "cab" is solvable if and only if "dab" is
						// solvable. So trying other new letters won't produce really "new"
						// words, but only words that are symmetric in the sense that they can
						// be transformed into each other by replacing one letter with another.
						// Avoiding these symmetries in the words we generate helps speeding up
						// this algorithm.
						break;
				}
			}

			// Done with this level, go to the next one
			currentLevel = nextLevel;
			nextLevel = new LinkedHashSet<>();
			if (operation.printStatus())
				System.out.println("Done with length " + currentLength + ". There were " + numUnsolvable
						+ " unsolvable words and " + currentLevel.size() + " solvable words.");
		}
	}

	// Normalize a word into the form that the above loop would generate it in. This means e.g. that the word ends
	// with the first letter of the alphabet.
	static public List<String> normalizeWord(List<String> word, SortedSet<String> alphabet)
	{
		List<String> result = new ArrayList<>();
		Map<String, String> morphism = new HashMap<>();
		Iterator<String> alphabetIter = alphabet.iterator();
		ListIterator<String> wordIter = word.listIterator(word.size());

		while (wordIter.hasPrevious()) {
			String letter = wordIter.previous();
			String replacement = morphism.get(letter);
			if (replacement == null) {
				assert alphabetIter.hasNext();
				replacement = alphabetIter.next();
				morphism.put(letter, replacement);
			}
			result.add(0, replacement);
		}
		return result;
	}

	static public void printWord(PrintStream stream, Collection<String> word) {
		StringBuilder builder = new StringBuilder();
		for (String c : word)
			builder.append(c);
		stream.println(builder.toString());
	}

	@Override
	public Category[] getCategories() {
		return new Category[]{Category.LTS};
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
