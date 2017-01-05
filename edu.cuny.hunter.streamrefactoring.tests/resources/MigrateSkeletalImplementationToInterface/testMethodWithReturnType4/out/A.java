package p;

class C {
}

interface I<T> {
	default T m() {
		return 0;
	}
}

abstract class A implements I<Integer> {
}
