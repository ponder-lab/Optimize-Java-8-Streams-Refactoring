package p;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class A {
	public static void main(String[] args) {
		Map map = new HashMap();
		map.entrySet().stream().count();
	}
}