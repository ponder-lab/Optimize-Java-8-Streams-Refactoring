package p;

interface I {
	void m() throws Throwable;
}

abstract class A implements I {
	@Override
	public void m() throws Throwable {
		this.finalize();
	}
}