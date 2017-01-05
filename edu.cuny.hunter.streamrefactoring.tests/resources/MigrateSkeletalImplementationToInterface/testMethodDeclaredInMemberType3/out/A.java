package p;

interface I {
	default void m() {
		B.this.getClass();
	}
}

class A extends B {
	abstract class B implements I {
	}
}
