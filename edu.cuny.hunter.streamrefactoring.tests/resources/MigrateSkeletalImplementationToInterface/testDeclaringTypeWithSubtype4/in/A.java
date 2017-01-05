package p;

interface I {
	void m();
}

interface J extends I {
	void m();
}

public class A implements I {

	@Override
	public void m() {
	}
}

class C extends A implements J {
}