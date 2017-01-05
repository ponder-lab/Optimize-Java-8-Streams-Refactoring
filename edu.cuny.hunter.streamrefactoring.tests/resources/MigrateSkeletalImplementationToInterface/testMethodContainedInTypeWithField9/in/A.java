package p;

interface I {
	void m();
}

public abstract class A implements I {
	protected static int f;

	@Override
	public void m() {
		int f2 = f;
	}
}
