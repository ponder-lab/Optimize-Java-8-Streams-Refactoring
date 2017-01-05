package p;

interface I {
	default void m() {
	}
}

abstract class A extends B {
}

abstract class B implements I {
}