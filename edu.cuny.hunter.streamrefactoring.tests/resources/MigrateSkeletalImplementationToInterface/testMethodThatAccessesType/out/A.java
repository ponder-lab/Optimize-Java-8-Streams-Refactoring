package p;

interface I {
	default void m() {
		A a;
	}
}

public abstract class A implements I {
}
