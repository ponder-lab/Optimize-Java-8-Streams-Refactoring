package p;

interface I {
	default void m() {
	}
}

interface J {
	void m();
}

public abstract class A implements I {
}

class B extends A implements J {
}