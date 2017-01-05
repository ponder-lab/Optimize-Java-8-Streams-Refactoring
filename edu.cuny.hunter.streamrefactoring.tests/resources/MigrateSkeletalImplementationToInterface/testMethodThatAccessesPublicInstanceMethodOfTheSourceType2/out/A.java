package p;

interface I {
	default void m() {
		new A() {
		}.n();
	}
}

abstract class A implements I {
	void n() {
	}
}