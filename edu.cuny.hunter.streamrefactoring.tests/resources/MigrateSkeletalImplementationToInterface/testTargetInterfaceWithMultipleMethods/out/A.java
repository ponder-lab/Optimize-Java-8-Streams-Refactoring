package p;

interface I {
	default void m() {
	}
	void n();
}

abstract class A implements I {
}
