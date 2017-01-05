package p;

interface I {
	default void m() {
	}
}

interface J {
	default void m() {
	}
}

public abstract class A implements I {
}

class B extends A implements J {
	@Override
	public void m() {
	}
}

class C extends B {
}