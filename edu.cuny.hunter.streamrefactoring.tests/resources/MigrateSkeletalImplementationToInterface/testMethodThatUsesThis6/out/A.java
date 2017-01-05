package p;

interface I {
	default void m() {
		A a = this;
	}
}

abstract class A implements I {
}