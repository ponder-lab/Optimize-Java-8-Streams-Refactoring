package p;

interface I {
	default void m() {
		int g = new B().f;
	}
}

class B {
	int f;
}

abstract class A extends B implements I {
}