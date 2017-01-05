package p;

interface I<X,T> {
	default void m() {
		T e = null;
	}
}

abstract class A<X,E> implements I<X,E> {
}
