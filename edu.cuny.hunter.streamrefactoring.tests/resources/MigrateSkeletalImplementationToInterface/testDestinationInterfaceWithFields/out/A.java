package p;

interface I {
	int f = 0;
	default void m() {
	}
}

abstract class A implements I {
}
