package p;

interface I {
	default void m() {
		new A() {
		}.f = 5;
	}
}

abstract class A implements I {
	int f;
}