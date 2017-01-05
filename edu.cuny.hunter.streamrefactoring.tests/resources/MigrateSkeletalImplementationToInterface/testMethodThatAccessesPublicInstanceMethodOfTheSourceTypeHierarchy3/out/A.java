package p;

interface I {
	default void m() {
		super.n();
	}
}

class B {
	void n() {
	}
}

abstract class A extends B implements I {
}