package p;

interface I {
	void m();
}

class B {
}

public abstract class A implements I {

	@Override
	public void m() {
		B b;
	}
}
