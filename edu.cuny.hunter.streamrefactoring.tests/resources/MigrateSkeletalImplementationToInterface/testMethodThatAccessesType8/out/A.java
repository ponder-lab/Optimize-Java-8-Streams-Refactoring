package p;

interface I<E extends B> {
	default void m() {
		E e;
	}
}

class B {
}

public abstract class A<E extends B> implements I<E> {
}
