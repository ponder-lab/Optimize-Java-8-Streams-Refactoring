package p;

interface I {
    default void m() {
    }
}

interface J {
    default void m() {
        I.super.m();
    }
}

abstract class A implements I, J {
}