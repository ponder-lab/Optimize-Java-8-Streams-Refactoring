package p;

interface I {
	void m();
}

public abstract class A implements I {
	
	static class C {
		static void n() {
		}
	}

	@Override
	public void m() {
		C.n();
	}
}
