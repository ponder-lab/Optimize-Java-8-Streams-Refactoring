package p;

interface I {
	default void m() {
	}

	default void n() {
	}
}

abstract class A implements I {
}
