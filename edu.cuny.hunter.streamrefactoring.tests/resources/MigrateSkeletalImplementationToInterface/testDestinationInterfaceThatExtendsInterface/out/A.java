package p;

interface J {
}

interface I extends J {
	default void m() {
	}
}

abstract class A implements I {
}
