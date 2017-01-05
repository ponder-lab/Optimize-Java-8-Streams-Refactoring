package p;

interface I {
	default void m() {
	}
}

interface J extends I {
	void m();
}

public class A implements I {
}

class C extends A implements J {
}