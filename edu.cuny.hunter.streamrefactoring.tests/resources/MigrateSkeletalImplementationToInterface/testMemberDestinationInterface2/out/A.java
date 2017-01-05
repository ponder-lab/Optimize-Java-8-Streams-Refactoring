package p;

class B {
	static interface I {
		default void m() {
		}
	}
}

abstract class A implements B.I {
}
