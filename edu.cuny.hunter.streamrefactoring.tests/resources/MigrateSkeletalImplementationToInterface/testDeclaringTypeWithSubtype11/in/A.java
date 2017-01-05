package p;

interface I {
	void m();
}

interface J {
	void m();
}

public class A implements I {
	@Override
	public void m() {
	}
}

class B extends A {
}

class C extends B implements J {
}