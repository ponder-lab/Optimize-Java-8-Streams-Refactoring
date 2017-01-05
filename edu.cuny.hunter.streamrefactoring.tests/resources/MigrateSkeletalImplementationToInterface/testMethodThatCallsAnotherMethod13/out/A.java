package p;

interface I {
	default void m() {
		System.out.println();
	}
}

abstract class A implements I {
}