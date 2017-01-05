package p;

interface I {
	void m();
}

public class A implements I {
	
	A() {
	}

	@Override
	public void m() {
		new A();
	}
}
