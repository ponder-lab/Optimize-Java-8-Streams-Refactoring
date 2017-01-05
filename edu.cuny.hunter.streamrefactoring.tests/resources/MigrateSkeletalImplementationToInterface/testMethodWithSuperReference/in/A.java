package p;

interface I {
    default void m() {
    }
}

interface J {
    void m();
}

abstract class A implements I, J {
    @Override
    public void m() {
        I.super.m();
    }
}