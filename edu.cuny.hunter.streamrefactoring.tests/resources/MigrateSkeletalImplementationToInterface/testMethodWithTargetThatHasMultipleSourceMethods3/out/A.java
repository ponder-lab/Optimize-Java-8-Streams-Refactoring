package p;

interface I {
	default void m() {
		n();
	}
	void n();
}

abstract class A implements I {
	@Override
	public void n() {
		System.out.println(5);
	}
}

abstract class B implements I {
	@Override
	public void n() {
		System.out.println(6);
	}
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 5.
		new B() {}.m(); //prints 6.
	}
}