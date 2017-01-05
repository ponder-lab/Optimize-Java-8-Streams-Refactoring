package p;

interface I {
	void m();
}

public abstract class A implements I {
	public int f;

	@Override
	public void m() {
		int f2 = f;
	}
}
