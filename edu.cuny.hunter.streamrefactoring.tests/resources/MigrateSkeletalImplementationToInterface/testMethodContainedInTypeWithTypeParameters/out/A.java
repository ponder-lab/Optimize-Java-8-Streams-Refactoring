package p;

interface I {
	default void m() {
	}
}

abstract class A<E> implements I {
}
