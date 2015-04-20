/*-
 * APT - Analysis of Petri Nets and labeled Transition systems
 * Copyright (C) 2015  Uli Schlachter
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

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.iterators.PeekingIterator;
import static org.apache.commons.collections4.iterators.PeekingIterator.peekingIterator;

/**
 * Instances of this class represent an equivalence relation over an unknown set. Initially, all elements are only
 * equivalent to themselves, but the equivalence classes of two elements can be joined.
 * @param <E> The type of elements in the equivalence relation.
 * @author Uli Schlachter
 */
public class EquivalenceRelation<E> extends AbstractCollection<Set<E>> implements Collection<Set<E>>, IEquivalenceRelation<E> {
	private final Map<E, Set<E>> elementToClass = new HashMap<E, Set<E>>();
	private final Set<Set<E>> allClasses = new HashSet<>();

	/**
	 * Refine this equivalence relation via another relation. This function splits classes in this equivalence
	 * relation where not all elements are in the same class in the given relation. The result will be the
	 * equivalence relation where two elements are in the same class iff they are in the same class in this and in
	 * the given relation.
	 * @param relation The relation to use for refinement.
	 * @return The refined equivalence relation or this relation if no refinement was necessary.
	 */
	public EquivalenceRelation<E> refine(IEquivalenceRelation<? super E> relation) {
		EquivalenceRelation<E> newRelation = new EquivalenceRelation<>();
		boolean hadSplit = false;
		for (Set<E> klass : allClasses) {
			Set<E> unhandled = new HashSet<>(klass);
			while (!unhandled.isEmpty()) {
				// Pick some element and figure out its equivalence class
				Iterator<E> it = unhandled.iterator();
				E e1 = it.next();
				it.remove();

				while (it.hasNext()) {
					E e2 = it.next();
					if (relation.isEquivalent(e1, e2)) {
						it.remove();
						newRelation.joinClasses(e1, e2);
					} else
						hadSplit = true;
				}
			}
		}

		if (!hadSplit)
			return this;
		return newRelation;
	}

	/**
	 * Join the equivalence classes of two elements.
	 * @param e1 The first element to join classes with
	 * @param e2 The other element to join classes with
	 * @return the new class containing both elements
	 */
	public Set<E> joinClasses(E e1, E e2) {
		Set<E> class1 = getClass(e1);
		Set<E> class2 = getClass(e2);

		if (class1.contains(e2))
			// Already in same class
			return class1;

		// Make class1 refer to the smaller of the two classes.
		if (class1.size() > class2.size()) {
			Set<E> tmp = class1;
			class1 = class2;
			class2 = tmp;
		}

		allClasses.remove(class1);
		allClasses.remove(class2);
		class2.addAll(class1);
		allClasses.add(class2);

		for (E e : class1)
			elementToClass.put(e, class2);

		return class2;
	}

	/**
	 * Get the equivalence class of the given element.
	 * @param e the element whose class is needed
	 * @return The element's equivalence class
	 */
	public Set<E> getClass(E e) {
		Set<E> result = elementToClass.get(e);
		if (result == null) {
			result = new HashSet<>();
			result.add(e);
			elementToClass.put(e, result);
			allClasses.add(result);
		}
		return result;
	}

	@Override
	public boolean isEquivalent(E e1, E e2) {
		if (e1.equals(e2))
			return true;
		Set<E> klass = elementToClass.get(e1);
		return klass != null && klass.contains(e2);
	}

	@Override
	public int size() {
		// Remove all classes which have only a single entry
		Iterator<Set<E>> iter = allClasses.iterator();
		while (iter.hasNext()) {
			Set<E> klass = iter.next();
			if (klass.size() == 1) {
				elementToClass.remove(klass.iterator().next());
				iter.remove();
			}
		}
		return allClasses.size();
	}

	@Override
	public Iterator<Set<E>> iterator() {
		return new Iterator<Set<E>>() {
			private PeekingIterator<Set<E>> iter = peekingIterator(allClasses.iterator());

			@Override
			public boolean hasNext() {
				// Skip all sets which have just a single entry
				Set<E> next = iter.peek();
				while (next != null && next.size() == 1) {
					iter.next();
					iter.remove();
					next = iter.peek();
				}
				return iter.hasNext();
			}

			@Override
			public Set<E> next() {
				hasNext();
				return Collections.unmodifiableSet(iter.next());
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof EquivalenceRelation))
			return false;
		EquivalenceRelation<?> rel = (EquivalenceRelation<?>) o;
		if (rel.size() != size())
			return false;
		return containsAll(rel);
	}

	@Override
	public int hashCode() {
		int result = 0;
		for (Set<E> set : this)
			result += set.hashCode();
		return result;
	}
}

// vim: ft=java:noet:sw=8:sts=8:ts=8:tw=120
