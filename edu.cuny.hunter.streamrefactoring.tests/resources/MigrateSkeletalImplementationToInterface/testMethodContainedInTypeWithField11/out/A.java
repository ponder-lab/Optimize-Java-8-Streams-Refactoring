package p;

import p.A.B;

interface I {
	default void m() {
		int f2 = B.f;
	}
}

public abstract class A implements I {
	
	class B {
		static final int f = 5;
	}
}
