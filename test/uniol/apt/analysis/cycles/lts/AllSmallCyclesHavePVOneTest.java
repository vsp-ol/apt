/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2012-2015  Members of the project group APT
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

import java.util.List;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static uniol.apt.adt.matcher.Matchers.*;
import uniol.apt.TestTSCollection;

import uniol.apt.adt.ts.TransitionSystem;
import uniol.apt.adt.ts.Arc;
import uniol.apt.analysis.exception.PreconditionFailedException;
import static uniol.apt.io.parser.ParserTestUtils.getAptLTS;

/**
 * @author Uli Schlachter, (Manuel Gieseking)
 */
@SuppressWarnings("unchecked") // I hate generics
public class AllSmallCyclesHavePVOneTest {
	static List<Arc> checkCycles(TransitionSystem ts) throws PreconditionFailedException {
		AllSmallCyclesHavePVOne check = new AllSmallCyclesHavePVOne(ts);
		if (check.smallCyclesHavePVOne()) {
			assertThat(check.getCounterExample(), empty());
			assertThat(check.noPV1CycleFound(), is(false));
			return null;
		}

		assertThat(check.noPV1CycleFound(), is(true));
		return check.getCounterExample();
	}

	static void checkCyclesHavePV1(TransitionSystem ts) throws PreconditionFailedException {
		assertThat(checkCycles(ts), is(nullValue()));
	}

	static void checkCyclesLargerPV1(TransitionSystem ts) throws PreconditionFailedException {
		assertThat(checkCycles(ts), empty());
	}

	@Test(expectedExceptions = PreconditionFailedException.class, expectedExceptionsMessageRegExp = "TS  is not deterministic")
	public void testNonDeterministicTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getNonDeterministicTS();
		new AllSmallCyclesHavePVOne(ts);
	}

	@Test(expectedExceptions = PreconditionFailedException.class, expectedExceptionsMessageRegExp = "TS  is not totally reachable")
	public void testSingleStateWithUnreachableTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getSingleStateWithUnreachableTS();
		new AllSmallCyclesHavePVOne(ts);
	}

	@Test(expectedExceptions = PreconditionFailedException.class, expectedExceptionsMessageRegExp = "TS  is not reversible")
	public void testThreeStatesTwoEdgesTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getThreeStatesTwoEdgesTS();
		new AllSmallCyclesHavePVOne(ts);
	}

	@Test(expectedExceptions = PreconditionFailedException.class, expectedExceptionsMessageRegExp = "TS  is not persistent")
	public void testDeterministicReachableReversibleNonPersistentTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getDeterministicReachableReversibleNonPersistentTS();
		new AllSmallCyclesHavePVOne(ts);
	}

	@Test
	public void testReversibleTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getReversibleTS();
		checkCyclesHavePV1(ts);
	}

	@Test
	public void testSingleStateLoop() throws Exception {
		TransitionSystem ts = TestTSCollection.getSingleStateLoop();
		checkCyclesHavePV1(ts);
	}

	@Test
	public void testSingleStateSingleTransitionTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getSingleStateSingleTransitionTS();
		checkCyclesHavePV1(ts);
	}

	@Test
	public void testSingleStateTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getSingleStateTS();
		checkCyclesHavePV1(ts);
	}

	@Test
	public void testTwoStateCycleSameLabelTS() throws Exception {
		TransitionSystem ts = TestTSCollection.getTwoStateCycleSameLabelTS();
		checkCyclesLargerPV1(ts);
	}

	@Test
	public void testOneCycle() throws Exception {
		TransitionSystem ts = getAptLTS("./nets/cycles/OneCycle-aut.apt");
		checkCyclesHavePV1(ts);
	}

	@Test
	public void testSmallerCycle() throws Exception {
		TransitionSystem ts = TestTSCollection.getDifferentCyclesTS();
		assertThat(checkCycles(ts), anyOf(
					contains(arcThatConnects("s11", "s12"), arcThatConnects("s12", "s11")),
					contains(arcThatConnects("s11", "s10"), arcThatConnects("s10", "s11")),
					contains(arcThatConnects("s11", "s21"), arcThatConnects("s21", "s11")),
					contains(arcThatConnects("s11", "s01"), arcThatConnects("s01", "s11"))
					));
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
