package p;

import java.util.AbstractCollection;

interface I<T extends AbstractCollection<T>> {
	default void m() {
		T e = null;
	}
}

abstract class A<E extends AbstractCollection<E>> implements I<E> {
}
