package edu.cuny.hunter.streamrefactoring.ui.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.cuny.hunter.streamrefactoring.core.utils.TypeVariableUtil;

public class TypeVariableUtilTest extends TypeVariableUtil {
	
	public TypeVariableUtilTest() {
		super();
	}

	@Test
	public void testStripBoundsFromFullyQualifiedParameterizedName() {
		assertEquals("A", stripBoundsFromFullyQualifiedParameterizedName("A"));
		assertEquals("A <E>", stripBoundsFromFullyQualifiedParameterizedName("A <E>"));
		assertEquals("A <E <F>>", stripBoundsFromFullyQualifiedParameterizedName("A <E <F>>"));
		assertEquals("A <E>", stripBoundsFromFullyQualifiedParameterizedName("A <E extends java.lang.String>"));
		assertEquals("A <E>", stripBoundsFromFullyQualifiedParameterizedName("A <E    extends java.lang.String>"));
		assertEquals("A <E <F>>", stripBoundsFromFullyQualifiedParameterizedName("A <E <F super java.lang.String>>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E extends F>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E extends F<G>>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E extends F<G extends H>>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E super F<G extends H>>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E extends F<G super H>>"));
		assertEquals("A<E>", stripBoundsFromFullyQualifiedParameterizedName("A<E super F<G super H>>"));
		
		assertEquals("A<E,T>", stripBoundsFromFullyQualifiedParameterizedName("A<E,T>"));
		assertEquals("A<E,T>", stripBoundsFromFullyQualifiedParameterizedName("A<E super F,T>"));
		assertEquals("A<E,T>", stripBoundsFromFullyQualifiedParameterizedName("A<E super F<G super H>,T>"));
		assertEquals("A<E,T>", stripBoundsFromFullyQualifiedParameterizedName("A<E super F<G super H>,T extends U>"));
	}

	@Test
	public void testFindEndingIndexOfBoundClause() {
		assertEquals(-1, findEndingIndexOfBoundClause("A", 0));
		assertEquals(-1, findEndingIndexOfBoundClause("A <E>", 2));
		assertEquals(-1, findEndingIndexOfBoundClause("A <E <F>>", 2));
		assertEquals(29, findEndingIndexOfBoundClause("A <E extends java.lang.String>", 5));
		assertEquals(31, findEndingIndexOfBoundClause("A <E   extends java.lang.String>", 7));
		assertEquals(30, findEndingIndexOfBoundClause("A <E <F super java.lang.String>>", 8));
	}

}
