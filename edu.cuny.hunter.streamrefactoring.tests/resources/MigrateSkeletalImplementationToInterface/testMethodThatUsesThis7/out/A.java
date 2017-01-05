package p;

interface I {
	default void m() {
		I i = this;
	}
}

abstract class A implements I {
}