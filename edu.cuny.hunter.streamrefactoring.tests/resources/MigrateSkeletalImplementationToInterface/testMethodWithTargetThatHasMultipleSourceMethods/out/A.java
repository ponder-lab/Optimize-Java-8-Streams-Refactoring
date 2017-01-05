package p;

interface I {
	default void m() {
		System.out.println(5);
	}
}

abstract class A implements I {
}

abstract class B implements I {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 5.
		new B() {}.m(); //prints 5.
	}
}