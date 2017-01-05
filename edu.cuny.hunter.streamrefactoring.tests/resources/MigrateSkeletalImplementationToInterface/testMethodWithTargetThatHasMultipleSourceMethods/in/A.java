package p;

interface I {
	void m();
}

abstract class A implements I {
	@Override
	public void m() {
		System.out.println(5);
	}
}

abstract class B implements I {
	@Override
	public void m() {
		System.out.println(5);
	}
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 5.
		new B() {}.m(); //prints 5.
	}
}