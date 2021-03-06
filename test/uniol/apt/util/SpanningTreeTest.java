/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2014  Uli Schlachter
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

package uniol.apt.util;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matcher;

import uniol.apt.TestTSCollection;
import uniol.apt.adt.ts.Arc;
import uniol.apt.adt.ts.State;
import uniol.apt.adt.ts.TransitionSystem;

import org.testng.annotations.Test;
import uniol.apt.adt.matcher.Matchers;
import static org.hamcrest.MatcherAssert.assertThat;
import static uniol.apt.adt.matcher.Matchers.*;

/** @author Uli Schlachter */
public class SpanningTreeTest {
	static private SpanningTree<TransitionSystem, Arc, State> get(TransitionSystem ts) {
		return SpanningTree.<TransitionSystem, Arc, State>get(ts);
	}

	static private SpanningTree<TransitionSystem, Arc, State> get(TransitionSystem ts, State init) {
		return SpanningTree.<TransitionSystem, Arc, State>get(ts, init);
	}

	static private SpanningTree<TransitionSystem, Arc, State> getReversed(TransitionSystem ts) {
		return SpanningTree.<TransitionSystem, Arc, State>getReversed(ts);
	}

	static private SpanningTree<TransitionSystem, Arc, State> getReversed(TransitionSystem ts, State init) {
		return SpanningTree.<TransitionSystem, Arc, State>getReversed(ts, init);
	}

	@Test
	public void testCache() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree1 = get(ts);
		SpanningTree<TransitionSystem, Arc, State> tree2 = get(ts);

		assertThat(tree2, sameInstance(tree1));
	}

	@Test
	public void testCacheReverse() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree1 = getReversed(ts);
		SpanningTree<TransitionSystem, Arc, State> tree2 = getReversed(ts);

		assertThat(tree2, sameInstance(tree1));
	}

	@Test
	public void testCacheMixed() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree1 = get(ts);
		SpanningTree<TransitionSystem, Arc, State> tree2 = getReversed(ts);

		assertThat(tree2, not(sameInstance(tree1)));
	}

	@Test
	public void testCacheClear() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree1f = get(ts);
		SpanningTree<TransitionSystem, Arc, State> tree1r = getReversed(ts);

		ts.createState();

		SpanningTree<TransitionSystem, Arc, State> tree2f = get(ts);
		SpanningTree<TransitionSystem, Arc, State> tree2r = getReversed(ts);

		assertThat(tree2f, not(sameInstance(tree1f)));
		assertThat(tree2r, not(sameInstance(tree1r)));
	}

	@Test
	public void testEmptyTS() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree = get(ts);

		assertThat(tree.getStartNode(), is(equalTo(null)));
		assertThat(tree.getUnreachableNodes(), is(empty()));
		assertThat(tree.getChords(), is(empty()));
	}

	@Test
	public void testEmptyTSReversed() {
		TransitionSystem ts = new TransitionSystem();

		SpanningTree<TransitionSystem, Arc, State> tree = getReversed(ts);

		assertThat(tree.getStartNode(), is(equalTo(null)));
		assertThat(tree.getUnreachableNodes(), is(empty()));
		assertThat(tree.getChords(), is(empty()));
	}

	private void verifySingleStateTS(SpanningTree<TransitionSystem, Arc, State> tree) {
		TransitionSystem ts = tree.getGraph();
		State s0 = ts.getNode("s0");

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), is(empty()));
		assertThat(tree.getChords(), is(empty()));

		assertThat(tree.getPredecessor(s0), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(s0), is(equalTo(null)));

		assertThat(tree.getPathFromStart(s0), contains(s0));
	}

	@Test
	public void testSingleStateTS() {
		TransitionSystem ts = TestTSCollection.getSingleStateTS();
		verifySingleStateTS(get(ts, ts.getInitialState()));
	}

	@Test
	public void testSingleStateTS2() {
		TransitionSystem ts = TestTSCollection.getSingleStateTS();
		verifySingleStateTS(get(ts));
	}

	@Test
	public void testSingleStateTSReversed() {
		TransitionSystem ts = TestTSCollection.getSingleStateTS();
		verifySingleStateTS(getReversed(ts, ts.getInitialState()));
	}

	@Test
	public void testSingleStateTS2Reversed() {
		TransitionSystem ts = TestTSCollection.getSingleStateTS();
		verifySingleStateTS(getReversed(ts));
	}

	static private Matcher<? super Arc> arcThatConnects(String source, String target) {
		return Matchers.arcThatConnects(source, target);
	}

	static private Matcher<? super Arc> arcThatConnects(boolean forward, String source, String target) {
		if (forward)
			return Matchers.arcThatConnects(source, target);
		else
			return Matchers.arcThatConnects(target, source);
	}

	static private void doCC1LTS(boolean forward) {
		TransitionSystem ts = TestTSCollection.getcc1LTS();
		State s0 = ts.getNode("s0");
		State s1 = ts.getNode("s1");
		State s2 = ts.getNode("s2");
		State s3 = ts.getNode("s3");

		SpanningTree<TransitionSystem, Arc, State> tree;
		if (forward)
			tree = get(ts, ts.getInitialState());
		else
			tree = getReversed(ts, ts.getInitialState());

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), is(empty()));

		List<Matcher<? super Arc>> edgeMatchers = new ArrayList<>();
		edgeMatchers.add(arcThatConnects(forward, "s1", "s0"));
		edgeMatchers.add(arcThatConnects(forward, "s2", "s0"));
		edgeMatchers.add(arcThatConnects(forward, "s3", "s1"));
		edgeMatchers.add(arcThatConnects(forward, "s3", "s2"));
		if (tree.getPredecessor(s3).equals(s1))
			edgeMatchers.add(arcThatConnects(forward, "s2", "s3"));
		else
			edgeMatchers.add(arcThatConnects(forward, "s1", "s3"));

		assertThat(tree.getChords(), containsInAnyOrder(edgeMatchers));

		assertThat(tree.getPredecessor(s0), is(equalTo(null)));
		assertThat(tree.getPredecessor(s1), is(equalTo(s0)));
		assertThat(tree.getPredecessor(s2), is(equalTo(s0)));
		assertThat(tree.getPredecessor(s3), is(anyOf(equalTo(s1), equalTo(s2))));

		assertThat(tree.getPredecessorEdge(s0), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(s1), is(arcThatConnects(forward, "s0", "s1")));
		assertThat(tree.getPredecessorEdge(s2), is(arcThatConnects(forward, "s0", "s2")));
		assertThat(tree.getPredecessorEdge(s3), is(either(
					arcThatConnects(forward, "s1", "s3")).or(
					arcThatConnects(forward, "s2", "s3"))));

		assertThat(tree.getPathFromStart(s0), contains(s0));
		assertThat(tree.getPathFromStart(s1), contains(s0, s1));
		assertThat(tree.getPathFromStart(s2), contains(s0, s2));
		assertThat(tree.getPathFromStart(s3), anyOf(contains(s0, s1, s3), contains(s0, s2, s3)));
	}

	@Test
	public void testcc1LTS() {
		doCC1LTS(true);
	}

	@Test
	public void testcc1LTSReversed() {
		doCC1LTS(false);
	}

	@Test
	public void testThreeStatesTwoEdgesTS() {
		TransitionSystem ts = TestTSCollection.getThreeStatesTwoEdgesTS();
		State s = ts.getNode("s");
		State t = ts.getNode("t");
		State v = ts.getNode("v");

		SpanningTree<TransitionSystem, Arc, State> tree = get(ts, ts.getInitialState());

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), is(empty()));
		assertThat(tree.getChords(), is(empty()));

		assertThat(tree.getPredecessor(s), is(equalTo(null)));
		assertThat(tree.getPredecessor(t), is(equalTo(s)));
		assertThat(tree.getPredecessor(v), is(equalTo(s)));

		assertThat(tree.getPredecessorEdge(s), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(t), is(arcThatConnects("s", "t")));
		assertThat(tree.getPredecessorEdge(v), is(arcThatConnects("s", "v")));

		assertThat(tree.getPathFromStart(s), contains(s));
		assertThat(tree.getPathFromStart(t), contains(s, t));
		assertThat(tree.getPathFromStart(v), contains(s, v));
	}

	@Test
	public void testThreeStatesTwoEdgesTSReversed() {
		TransitionSystem ts = TestTSCollection.getThreeStatesTwoEdgesTS();
		State s = ts.getNode("s");
		State t = ts.getNode("t");
		State v = ts.getNode("v");

		SpanningTree<TransitionSystem, Arc, State> tree = getReversed(ts, ts.getInitialState());

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), is(containsInAnyOrder(t, v)));
		assertThat(tree.getChords(), is(empty()));

		assertThat(tree.getPredecessor(s), is(equalTo(null)));
		assertThat(tree.getPredecessor(t), is(equalTo(null)));
		assertThat(tree.getPredecessor(v), is(equalTo(null)));

		assertThat(tree.getPredecessorEdge(s), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(t), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(v), is(equalTo(null)));

		assertThat(tree.getPathFromStart(s), contains(s));
		assertThat(tree.getPathFromStart(t), empty());
		assertThat(tree.getPathFromStart(v), empty());
	}

	@Test
	public void testPersistentTS() {
		TransitionSystem ts = TestTSCollection.getPersistentTS();
		State s0 = ts.getNode("s0");
		State l = ts.getNode("l");
		State r = ts.getNode("r");
		State s1 = ts.getNode("s1");

		SpanningTree<TransitionSystem, Arc, State> tree = get(ts, ts.getInitialState());

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), is(empty()));

		if (tree.getPredecessor(s1).equals(l))
			assertThat(tree.getChords(), contains(arcThatConnects("r", "s1")));
		else
			assertThat(tree.getChords(), contains(arcThatConnects("l", "s1")));

		assertThat(tree.getPredecessor(s0), is(equalTo(null)));
		assertThat(tree.getPredecessor(l), is(equalTo(s0)));
		assertThat(tree.getPredecessor(r), is(equalTo(s0)));
		assertThat(tree.getPredecessor(s1), is(anyOf(equalTo(l), equalTo(r))));

		assertThat(tree.getPredecessorEdge(s0), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(l), is(arcThatConnects("s0", "l")));
		assertThat(tree.getPredecessorEdge(r), is(arcThatConnects("s0", "r")));
		assertThat(tree.getPredecessorEdge(s1),
				is(either(arcThatConnects("l", "s1")).or(arcThatConnects("r", "s1"))));

		assertThat(tree.getPathFromStart(s0), contains(s0));
		assertThat(tree.getPathFromStart(l), contains(s0, l));
		assertThat(tree.getPathFromStart(r), contains(s0, r));
		assertThat(tree.getPathFromStart(s1), anyOf(contains(s0, l, s1), contains(s0, r, s1)));
	}

	@Test
	public void testNotTotallyReachableTS() {
		TransitionSystem ts = TestTSCollection.getNotTotallyReachableTS();
		State s0 = ts.getNode("s0");
		State s1 = ts.getNode("s1");
		State fail = ts.getNode("fail");

		SpanningTree<TransitionSystem, Arc, State> tree = get(ts, ts.getInitialState());

		assertThat(tree.getStartNode(), is(equalTo(ts.getInitialState())));
		assertThat(tree.getUnreachableNodes(), contains(fail));
		assertThat(tree.getChords(), is(empty()));

		assertThat(tree.getPredecessor(s0), is(equalTo(null)));
		assertThat(tree.getPredecessor(s1), is(equalTo(s0)));
		assertThat(tree.getPredecessor(fail), is(equalTo(null)));

		assertThat(tree.getPredecessorEdge(s0), is(equalTo(null)));
		assertThat(tree.getPredecessorEdge(s1), is(arcThatConnects("s0", "s1")));
		assertThat(tree.getPredecessorEdge(fail), is(equalTo(null)));

		assertThat(tree.getPathFromStart(s0), contains(s0));
		assertThat(tree.getPathFromStart(s1), contains(s0, s1));
		assertThat(tree.getPathFromStart(fail), is(empty()));
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
