package p;

interface I {
	int f = 5;

	default void m() {
		int f = 0;
		System.out.println(f);
	}
}

abstract class A implements I {
}

class Main {
	public static void main(String[] args) {
		new A() {
		}.m(); // prints 0.
	}
}
