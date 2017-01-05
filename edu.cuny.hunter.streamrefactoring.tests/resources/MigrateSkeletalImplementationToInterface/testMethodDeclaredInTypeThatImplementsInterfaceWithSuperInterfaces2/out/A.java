package p;

interface I {
}

interface J extends I {
	default void m() {
	}
}

abstract class B implements J {
}

abstract class A extends B {
}
