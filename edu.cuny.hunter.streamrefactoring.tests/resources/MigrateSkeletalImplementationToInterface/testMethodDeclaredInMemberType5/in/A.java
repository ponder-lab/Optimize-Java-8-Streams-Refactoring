package p;

interface I {
	void m();	
}

class A {

	class B implements I {
		@Override
		public void m() {
			A.super.getClass();
		}
	}
}
