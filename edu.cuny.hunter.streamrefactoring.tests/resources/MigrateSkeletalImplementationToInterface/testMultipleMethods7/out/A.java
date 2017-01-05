package p;

interface I {
	default void m() {
		n();
	}
	void n();
}

public abstract class A implements I {
}
