package p;

interface I {
	default void m() throws Throwable {
		this.finalize();
	}
}

abstract class A implements I {
}