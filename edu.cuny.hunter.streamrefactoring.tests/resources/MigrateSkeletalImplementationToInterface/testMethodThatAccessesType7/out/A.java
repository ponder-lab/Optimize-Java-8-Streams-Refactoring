package p;

interface I {
	default void m() {
		E e;
	}
}

class B {
}

public abstract class A<E extends B> implements I {
}
