package p;

interface I {
	default void m() {
	}
}

abstract class A implements I {
}

class B extends A {
}