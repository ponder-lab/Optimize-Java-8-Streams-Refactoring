package p;

interface I {
	void m() throws CloneNotSupportedException;
}

abstract class A implements I {
	@Override
	public void m() throws CloneNotSupportedException {
		this.clone();
	}
}