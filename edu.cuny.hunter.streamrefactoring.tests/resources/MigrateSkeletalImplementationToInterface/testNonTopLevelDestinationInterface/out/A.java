package p;

class B {
	interface I {
		default void m() {
		}
	}
}

abstract class A implements B.I {
}
