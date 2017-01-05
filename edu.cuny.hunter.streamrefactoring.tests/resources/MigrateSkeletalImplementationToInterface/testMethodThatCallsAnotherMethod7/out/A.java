package p;

import p.A.C;

interface I {
	default void m() {
		C.n();
	}
}

public abstract class A implements I {
	
	static class C {
		static void n() {
		}
	}
}
