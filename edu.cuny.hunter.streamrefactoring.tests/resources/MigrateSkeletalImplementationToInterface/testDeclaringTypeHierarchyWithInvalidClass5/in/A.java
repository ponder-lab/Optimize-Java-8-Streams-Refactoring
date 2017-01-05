package p;

interface I {
	void m();

	void n();
}

class B {
	public void n() {
		System.out.println("B.n()");
	}
}

abstract class A extends B implements I {
	@Override
	public void m() {
		n();
	}
}