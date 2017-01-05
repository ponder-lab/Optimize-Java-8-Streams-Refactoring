package p;

interface I {
	void m();
}

class B {
	void m() {
	}
}

abstract class A implements I {
	@Override
	public void m() {
		new B().m();
	}
}