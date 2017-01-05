package p;

interface I {
	default void m() {
		int g = super.f;
	}
}

class B {
	int f;
}

abstract class A extends B implements I {
}