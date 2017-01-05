package p;

import p.A.C;

interface I {
	default void m() {
		new C().n();
	}
}

public abstract class A implements I {
	
	class C {
		void n() {
		}
	}
}
