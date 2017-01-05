package p;

interface I {
	void m();
	void n();
}

public abstract class A implements I {

	@Override
	public void m() {
		n();
	}

	@Override
	public void n() {
	}
}
