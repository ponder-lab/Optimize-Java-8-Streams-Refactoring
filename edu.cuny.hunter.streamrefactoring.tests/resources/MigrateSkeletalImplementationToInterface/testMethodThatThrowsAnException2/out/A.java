package p;

interface I {
	default void m() throws Exception {
	}
}

abstract class A implements I {
}
