package p;

class C {
}

interface I<T> {
	T m();
}

abstract class A implements I<Integer> {
	@Override
	public Integer m() {
		return 0;
	}
}
