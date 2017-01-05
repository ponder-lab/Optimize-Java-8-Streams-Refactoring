package p;

import p.A.B;

interface I {
	default <E extends B> void m() {
		E e;
	}
}

public abstract class A implements I {

	class B {
	}
}
