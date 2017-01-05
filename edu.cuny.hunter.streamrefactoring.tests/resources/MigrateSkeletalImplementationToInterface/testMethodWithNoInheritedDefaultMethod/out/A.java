package p;

interface I {
}

interface J {
	default void m() {
	}
}

abstract class A implements I, J {
}