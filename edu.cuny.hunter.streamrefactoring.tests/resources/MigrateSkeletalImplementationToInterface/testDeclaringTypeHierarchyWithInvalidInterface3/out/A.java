package p;

interface I extends J {
}

interface J {
	default void m() {
	}
}

abstract class A implements I {
}
