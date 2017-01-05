package p;

interface I {
	default void m() {
	}
}

abstract class B implements I {
}

abstract class A extends B implements I {
	public void m() {
	}
}
