package p;

interface I extends J {
	default void m() {
	}
}

interface J {
}

abstract class A implements I {
}
