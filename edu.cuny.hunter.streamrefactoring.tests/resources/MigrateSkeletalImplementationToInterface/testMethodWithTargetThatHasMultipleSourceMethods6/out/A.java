package p;

interface I {
	default void m() {
		System.out.println(6);
	}
}

abstract class A implements I {
	@Override
	public void m() {
		System.out.println(5);
	}
}

abstract class B implements I {
}

abstract class C implements I {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 5.
		new B() {}.m(); //prints 6.
		new C() {}.m(); //prints 6.
	}
}