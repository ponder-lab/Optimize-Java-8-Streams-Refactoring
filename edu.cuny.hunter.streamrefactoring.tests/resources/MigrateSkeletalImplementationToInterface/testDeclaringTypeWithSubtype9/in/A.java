package p;

interface I {
	void m();
}

interface J {
	default void m() {
	}
}

public abstract class A implements I {
	@Override
	public void m() {
	}
}

class B extends A implements J {
}

class C extends B {

	@Override
	public void m() {
	}	
}
