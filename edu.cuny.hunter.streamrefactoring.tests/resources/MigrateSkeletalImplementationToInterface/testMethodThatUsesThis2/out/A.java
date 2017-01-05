package p;

interface I {
	default void m() {
		this.getClass();
	}
}

public abstract class A implements I {
}
