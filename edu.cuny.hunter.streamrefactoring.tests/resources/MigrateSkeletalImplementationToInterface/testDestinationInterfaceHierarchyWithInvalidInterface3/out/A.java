package p;

interface I {
	default void m() {
	}
}

interface J extends I {
}

abstract class A implements J {
}
