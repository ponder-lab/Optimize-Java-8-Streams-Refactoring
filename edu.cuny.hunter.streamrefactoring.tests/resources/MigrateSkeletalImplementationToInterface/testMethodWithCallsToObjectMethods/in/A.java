package p;

interface I {
	void m() throws InterruptedException;
}

abstract class A implements I {
	@Override
	public void m() throws InterruptedException {
		this.equals(null);
		this.getClass();
		this.hashCode();
		notify();
		notifyAll();
		toString();
		wait();
		wait(1);
		wait(1, 1);
	}
}