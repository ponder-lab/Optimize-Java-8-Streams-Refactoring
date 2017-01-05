package p;

interface I {
	void m();
}

class B {
	void n() {	
	}
}

abstract class A extends B implements I {
	@Override
	public void m() {
		n(); //this method can't be accessed from I.
	}
}