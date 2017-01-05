package p;

interface I {
	void m();
}

class B {
	void n() {
	}
}

abstract class A extends B implements I {

	@Override
	public void m() {
		new B().n();
	}
}