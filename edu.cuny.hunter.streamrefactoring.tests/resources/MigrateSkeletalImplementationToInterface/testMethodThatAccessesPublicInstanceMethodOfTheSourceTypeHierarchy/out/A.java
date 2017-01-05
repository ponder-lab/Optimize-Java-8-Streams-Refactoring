package p;

interface I {
	default void m() {
		n();
	}
}

class B {
	void n() {
	}
}

abstract class A extends B implements I {
}