package p;

interface I {
	default void m() {
		super.f = 5;
	}
}

class B {
	int f;
}

abstract class A extends B implements I {
}