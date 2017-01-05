package p;

interface I {
	default void m() {
	}
}

class A {
	abstract class B implements I {
	}
}
