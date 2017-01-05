package p;

interface I {
	default void m() {
		A a = this;
		a.n();
	}
}

abstract class A implements I {
	void n() {
	}
}
