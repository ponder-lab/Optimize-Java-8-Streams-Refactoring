package p;

interface I {
	default void m() {
		this.m();
	}
}

public abstract class A implements I {
}
