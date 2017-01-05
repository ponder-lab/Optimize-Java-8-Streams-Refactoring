package p;

interface I {
	default void m() {
		new B().n();
	}
}

class B {
	void n() {
	}
}

abstract class A extends B implements I {
}