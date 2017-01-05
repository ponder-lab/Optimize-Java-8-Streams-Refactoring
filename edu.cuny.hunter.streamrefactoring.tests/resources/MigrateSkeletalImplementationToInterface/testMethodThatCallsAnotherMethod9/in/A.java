package p;

interface I {
	void m();
}

public abstract class A implements I {
	
	public static class C {
		private static void n() {
		}
	}

	@Override
	public void m() {
		C.n();
	}
}
