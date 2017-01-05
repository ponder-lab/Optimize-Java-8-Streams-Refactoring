package p;

interface I {
	default void m() {
		int f = 5;
		new B().f = f;
	}
}

class B {
	int f;
}

abstract class A implements I {
}