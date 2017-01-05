package p;

interface I {
	default void m() {
	}
}

abstract class A implements I {
}

abstract class B implements I {
}