package p;

interface I {
	default void m() throws InterruptedException {
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

abstract class A implements I {
}