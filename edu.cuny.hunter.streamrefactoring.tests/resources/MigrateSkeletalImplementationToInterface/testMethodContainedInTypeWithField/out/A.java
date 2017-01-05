package p;

interface I {
	default void m() {
	}
}

public abstract class A implements I {
	int f;
}
