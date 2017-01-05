package p;

interface I {
	default void m() {
		E e = null;
	}
}

abstract class A<E> implements I {
}
