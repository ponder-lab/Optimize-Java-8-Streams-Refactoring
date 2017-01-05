package p;

interface I {
	void m();
}

class B {
	int f;
}

abstract class A implements I {
	@Override
	public void m() {
		int f = new B().f;
	}
}