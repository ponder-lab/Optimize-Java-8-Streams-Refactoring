package p;

interface I<T> {
	void m();
}

abstract class A<T> implements I<T> {
	@Override
	public void m() {
		T t = null;
	}
}
