package p;

import p.A.B;

interface I {
	default void m() {
		B b;
	}
}

public abstract class A implements I {

	class B {
	}
}
