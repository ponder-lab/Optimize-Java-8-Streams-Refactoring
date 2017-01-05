package p;

interface I<E> {
	default void m() {
		E e = null;
	}
}

abstract class A<E> implements I<E> {
}
