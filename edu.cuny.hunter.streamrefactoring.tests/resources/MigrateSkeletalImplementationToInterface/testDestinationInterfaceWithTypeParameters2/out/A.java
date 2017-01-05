package p;

interface I<T> {
	default void m() {
		T e = null;
	}
}

abstract class A<E> implements I<E> {
}
