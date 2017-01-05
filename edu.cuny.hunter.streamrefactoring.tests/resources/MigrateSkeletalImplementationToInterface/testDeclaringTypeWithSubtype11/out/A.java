package p;

interface I {
	default void m() {
	}
}

interface J {
	void m();
}

public class A implements I {
}

class B extends A {
}

class C extends B implements J {
}