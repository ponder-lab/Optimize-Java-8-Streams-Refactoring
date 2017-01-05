package p;

interface I {
	default void m() {
		new A().n();
	}
}

class A implements I {
	void n() {
	}
}