package p;

interface I {
}

interface J extends I {
	default void m() {
	}
}

abstract class A implements J {
}

abstract class B implements I {
}