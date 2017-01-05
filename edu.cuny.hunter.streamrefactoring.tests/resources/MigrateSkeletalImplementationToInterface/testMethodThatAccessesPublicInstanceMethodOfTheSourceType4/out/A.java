package p;

interface I {
	default void m() {
		this.n();
	}
}

abstract class A implements I {
	void n() {
	}
}