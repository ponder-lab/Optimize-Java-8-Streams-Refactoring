package p;

interface I {
	default void m(I i) {
		m(this);
	}
}

abstract class A implements I {
}