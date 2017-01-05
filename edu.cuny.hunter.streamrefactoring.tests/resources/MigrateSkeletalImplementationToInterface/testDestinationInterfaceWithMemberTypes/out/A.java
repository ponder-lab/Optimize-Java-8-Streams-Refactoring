package p;

interface I {
	default void m() {
	}

	class B {
	}
}

abstract class A implements I {
}
