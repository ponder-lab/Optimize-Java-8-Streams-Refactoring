package p;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class A {
	public static void main(String[] args) {
		Map<Object, Object> map = new HashMap<>();
		Map<Object, Object> map2 = map.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
	}
}