package p;

interface I {
	void m();

	void n();
}

abstract class B implements I {
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