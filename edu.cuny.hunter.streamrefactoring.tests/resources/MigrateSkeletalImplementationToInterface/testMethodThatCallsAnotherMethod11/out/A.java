package p;

interface I {
	default void m() {
		n();
	}
	default void n() {
	}
}

public abstract class A implements I {

	@Override
	public void n() {
	}
}
