package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

abstract class A implements I {
}
