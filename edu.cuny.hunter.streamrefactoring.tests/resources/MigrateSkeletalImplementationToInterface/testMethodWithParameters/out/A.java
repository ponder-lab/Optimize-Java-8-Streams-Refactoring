package p;

interface I {
	default void m(int n) {
	}
}

abstract class A implements I {
}
