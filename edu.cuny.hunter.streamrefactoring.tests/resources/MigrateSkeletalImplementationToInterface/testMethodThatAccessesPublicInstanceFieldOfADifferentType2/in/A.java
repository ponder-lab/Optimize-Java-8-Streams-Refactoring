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
		int f = 5;
		new B().f = f;
	}
}