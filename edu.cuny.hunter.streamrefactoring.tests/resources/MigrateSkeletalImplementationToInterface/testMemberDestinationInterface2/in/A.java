package p;

class B {
	static interface I {
		void m();
	}
}

abstract class A implements B.I {
	@Override
	public void m() {
	}
}
