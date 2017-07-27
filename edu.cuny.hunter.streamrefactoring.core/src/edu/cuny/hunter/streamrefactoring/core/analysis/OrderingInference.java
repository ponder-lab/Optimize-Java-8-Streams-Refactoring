package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Set;
import java.util.Spliterator;

import org.eclipse.jdt.core.IMethod;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.types.TypeReference;

class OrderingInference {

	private OrderingInference() {
	}

	static Ordering inferOrdering(Set<TypeAbstraction> possibleTypes, IMethod calledMethod)
			throws InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException {
		Ordering ret = null;
	
		for (TypeAbstraction typeAbstraction : possibleTypes) {
			if (typeAbstraction != TypeAbstraction.TOP) {
				Ordering ordering = inferOrdering(typeAbstraction, calledMethod);
	
				if (ret == null)
					ret = ordering;
				else if (ret != ordering)
					throw new InconsistentPossibleOrderingException(
							ret + " does not match " + ordering + " for type: " + typeAbstraction + ".");
			}
		}
	
		return ret;
	}

	// TODO: Cache this?
	static Ordering inferOrdering(String className, IMethod calledMethod)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		try {
			Class<?> clazz = Class.forName(className);
	
			// is it instantiable?
			if (!Util.isAbstractType(clazz)) {
				Object instance = Stream.createInstance(clazz);
				boolean ordered;
	
				Spliterator<?> spliterator = Stream.getSpliterator(instance, calledMethod);
				ordered = spliterator.hasCharacteristics(Spliterator.ORDERED);
				// TODO: Can we use something other than reflection,
				// like static analysis? Also, it may be an abstract
				// class.
	
				// FIXME: What if there is something under this that is
				// ordered? I guess this applies to both intra and
				// interprocedural analysis but more for the former.
				if (!ordered)
					return Ordering.UNORDERED;
				else
					return Ordering.ORDERED;
			} else
				throw new NoninstantiableException(clazz + " cannot be instantiated because it is an interface.",
						clazz);
		} catch (ClassNotFoundException e) {
			// TODO Not sure what we should do in this situation. What if we
			// can't instantiate the iterable? Is there another way to find out
			// this information? This could be a problem in third-party
			// container libraries. Also, what if we don't have the class in the
			// classpath?
			e.printStackTrace();
			throw new RuntimeException("Can't find: " + className, e);
		}
	}

	private static Ordering inferOrdering(TypeAbstraction type, IMethod calledMethod)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		TypeReference typeReference = type.getTypeReference();
		String binaryName = Stream.getBinaryName(typeReference);
		return inferOrdering(binaryName, calledMethod);
	}

}
