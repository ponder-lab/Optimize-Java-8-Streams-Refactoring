package p;

interface I {
	default void m() {
		B b;
	}
}

class B {
}

public abstract class A implements I {
}
