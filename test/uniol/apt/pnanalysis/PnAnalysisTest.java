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

package uniol.apt.pnanalysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import uniol.apt.TestNetCollection;
import uniol.apt.adt.pn.Marking;
import uniol.apt.adt.pn.PetriNet;
import uniol.apt.analysis.exception.PreconditionFailedException;
import uniol.apt.io.parser.ParserTestUtils;
import uniol.apt.io.parser.impl.exception.FormatException;
import static org.testng.Assert.fail;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.Test;
import uniol.apt.adt.pn.Node;
import uniol.apt.adt.pn.Place;
import uniol.apt.adt.pn.Transition;
import uniol.apt.analysis.isomorphism.IsomorphismLogic;
import uniol.apt.io.renderer.impl.APTRenderer;
import uniol.apt.module.exception.ModuleException;
import uniol.tests.dataprovider.IntRangeDataProvider;
import uniol.tests.dataprovider.annotations.IntRangeParameter;

/**
 * @author Manuel Gieseking
 */
public class PnAnalysisTest {

	@Test
	public void testPreconditionFailedTest() throws IOException, FormatException, ModuleException {
		PnAnalysis ana = new PnAnalysis();
		try {
			PetriNet pn = ParserTestUtils.getAptPN("nets/crashkurs-cc2-net.apt");
			ana.checkAllIsomorphicTSystemsForPetriNet(pn, 42, 1, false);
			fail("Not recogniced that net is not plain.");
		} catch (PreconditionFailedException ex) {
			assertEquals(ex.getMessage(), "The input Petri net is not plain.");
		}
		try {
			PetriNet pn = ParserTestUtils.getAptPN("nets/crashkurs-cc1-net.apt");
			ana.checkAllIsomorphicTSystemsForPetriNet(pn, 42, 1, false);
			fail("Not recogniced that net has no k-marking >=2.");
		} catch (PreconditionFailedException ex) {
			assertEquals(ex.getMessage(), "The input Petri net has no k-marking >=2.");
		}
	}

	@Test
	public void testIsomorphism() throws IOException, FormatException, ModuleException {
		PetriNet pn1 = ParserTestUtils.getAptPN("nets/ksysT-net-aut-net.apt");
		PetriNet pn2 = ParserTestUtils.getAptPN("./nets/EB-PhD-Fundamenta.apt");
		IsomorphismLogic logic = new IsomorphismLogic(pn1, pn2, false);
		assertTrue(logic.isIsomorphic());
	}

	@Test
	public void testReservoirSampling() {
		PetriNet net = RandomTNetGenerator.reservoirSampling(2, 2);
		assertNotNull(net);
		net = RandomTNetGenerator.reservoirSampling(1, 1);
		assertNotNull(net);
		net = RandomTNetGenerator.reservoirSampling(1, 0);
		assertNotNull(net);
		net = RandomTNetGenerator.reservoirSampling(5, 2);
		assertNotNull(net);
		net = RandomTNetGenerator.reservoirSampling(6, 12);
		assertNotNull(net);
	}

	@Test(enabled = false) // This test needs sometimes more than hour (instead of a minute) => disable it
	public void testRandom() throws IOException, FormatException, ModuleException {
		PetriNet pn = ParserTestUtils.getAptPN("./nets/EB-PhD-Fundamenta.apt");
		PnAnalysis ana = new PnAnalysis();
		PetriNet net = ana.checkAllIsomorphicTSystemsForPetriNet(pn, 20, 10, true);
		if (net != null) {
			IsomorphismLogic logic = new IsomorphismLogic(pn, net, true);
			assertTrue(logic.isIsomorphic());
			APTRenderer renderer = new APTRenderer();
			File file = new File("./nets/tnetIsomorphToEB-PhD-Fundamenta.apt");
			try (FileWriter writer = new FileWriter(file, true)) {
				writer.write(renderer.render(net));
				writer.flush();
			}
		}
	}

	@Test(dataProvider = "IntRange", dataProviderClass = IntRangeDataProvider.class)
	@IntRangeParameter(start = 1, end = 100)
	public void testRandomItself(int g) throws ModuleException {
		PetriNet net = RandomTNetGenerator.createRandomTNet(g);
		assertTrue(net.getPlaces().size() <= g, "size places: " + net.getPlaces().size() + " <= " + g);
		assertTrue(net.getTransitions().size() < 2 * net.getPlaces().size() + 1);
		for (Place place : net.getPlaces()) {
			assertEquals(1, place.getPostsetNodes().size());
			assertEquals(1, place.getPresetNodes().size());
		}
		int dead = 0;
		for (Transition transition : net.getTransitions()) {
			Set<Node> post = transition.getPostsetNodes();
			Set<Node> pre = transition.getPresetNodes();
			if (post.isEmpty() && pre.isEmpty()) {
				++dead;
				continue;
			}
			if (post.isEmpty() || pre.isEmpty()) {
				fail("Exists Transition with empty pre- or postset");
			}
		}
		assertTrue(dead < 2, "dead < 2");
	}

	@Test(enabled = false)
	public void test() throws IOException, FormatException, ModuleException {
		PetriNet pn = ParserTestUtils.getAptPN("./nets/EB-PhD-Fundamenta.apt");
		PnAnalysis ana = new PnAnalysis();
		assertNotEquals(null, ana.checkAllIsomorphicTSystemsForPetriNet(pn, 8, 2, false));
	}

	@Test
	public void testOnePlaceNet() throws IOException, FormatException, ModuleException {
		PetriNet pn = TestNetCollection.getNoTransitionOnePlaceNet();
		pn.setInitialMarking(new Marking(pn, 5));
		PnAnalysis ana = new PnAnalysis();
		assertNotEquals(null, ana.checkAllIsomorphicTSystemsForPetriNet(pn, 4, 2, false));
		assertEquals(1, ana.checkAllIsomorphicTSystemsForPetriNet(pn, 4, 2, false).getPlaces().size());
		assertEquals(1, ana.checkAllIsomorphicTSystemsForPetriNet(pn, 4, 2, false).getTransitions().size());
		assertEquals(2, ana.checkAllIsomorphicTSystemsForPetriNet(pn, 4, 2, false).getEdges().size());
	}

	@Test
	public void testOneTransitionNet() throws PreconditionFailedException {
		try {
			// Same for emptynet.
			PetriNet pn = TestNetCollection.getOneTransitionNoPlaceNet();
			PnAnalysis ana = new PnAnalysis();
			ana.checkAllIsomorphicTSystemsForPetriNet(pn, 4, 2, false);
		} catch (PreconditionFailedException ex) {
			assertEquals(ex.getMessage(), "The input Petri net has no k-marking >=2.");
		}
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
