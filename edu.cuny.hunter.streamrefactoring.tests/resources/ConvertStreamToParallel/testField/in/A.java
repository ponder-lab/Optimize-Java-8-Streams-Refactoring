package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

    Stream<Object> stream;
    
    void m() {
        stream = new HashSet<>().stream().parallel();
    }

    @EntryPoint
    void n() {
    	m();
        stream.distinct().count();
    }

}