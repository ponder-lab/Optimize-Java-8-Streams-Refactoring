package p;

interface I {
	default void m(A a) {
		m(this);
	}
}

abstract class A implements I {
}