package p;

interface I {
	default void m() {
		A.this.getClass();
	}
}

class A {
	abstract class B implements I {
	}
}
