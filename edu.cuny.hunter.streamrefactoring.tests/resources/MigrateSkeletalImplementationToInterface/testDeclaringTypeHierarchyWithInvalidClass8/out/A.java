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

abstract class B extends A implements J {
}

class C extends B {	
}