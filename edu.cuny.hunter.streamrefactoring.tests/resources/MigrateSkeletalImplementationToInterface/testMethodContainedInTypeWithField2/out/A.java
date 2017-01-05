package p;

interface I {
	default void m() {
		int f;
		f = 5;
	}
}

public abstract class A implements I {
	int f;
}
