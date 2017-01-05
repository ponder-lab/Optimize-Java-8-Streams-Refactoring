package p;

interface I {
	void m();
	void n();
}

abstract class A implements I {
	@Override
	public void m() {
		n();
	}
	
	@Override
	public void n() {
		System.out.println(5);
	}
}

abstract class B implements I {
	@Override
	public void m() {
		n();
	}
	
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