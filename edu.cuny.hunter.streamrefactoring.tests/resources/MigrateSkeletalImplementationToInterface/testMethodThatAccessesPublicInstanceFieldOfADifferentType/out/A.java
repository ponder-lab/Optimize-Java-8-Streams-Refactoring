package p;

interface I {
	default void m() {
		int f = new B().f;
	}
}

class B {
	int f;
}

abstract class A implements I {
}