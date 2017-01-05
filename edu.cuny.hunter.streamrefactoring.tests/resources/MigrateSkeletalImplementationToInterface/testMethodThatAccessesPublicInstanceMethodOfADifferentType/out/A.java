package p;

interface I {
	default void m() {
		new B().m();
	}
}

class B {
	void m() {
	}
}

abstract class A implements I {
}