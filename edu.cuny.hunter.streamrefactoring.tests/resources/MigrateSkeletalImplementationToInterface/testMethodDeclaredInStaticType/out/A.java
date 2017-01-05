package p;

interface I {
	default void m() {
	}
}

class A {
	static abstract class B implements I {
	}
}
