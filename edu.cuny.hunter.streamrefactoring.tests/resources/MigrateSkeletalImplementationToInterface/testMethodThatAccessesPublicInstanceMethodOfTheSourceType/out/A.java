package p;

interface I {
	default void m() {
		n();
	}
}

abstract class A implements I {
	void n() {
	}
}