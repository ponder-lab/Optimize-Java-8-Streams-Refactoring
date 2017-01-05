package p;

interface I {
	void m();
}

interface J {
	void m();
}

public abstract class A implements I {

	@Override
	public void m() {
	}
}

abstract class B extends A implements J {
}

class C extends B {
}