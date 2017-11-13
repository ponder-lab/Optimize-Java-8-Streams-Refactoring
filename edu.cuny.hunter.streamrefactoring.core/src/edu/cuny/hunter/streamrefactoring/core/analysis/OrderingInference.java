package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.Spliterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.BaseStream;

import org.eclipse.jdt.core.IMethod;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;

class OrderingInference {

	private Objenesis objenesis = new ObjenesisStd();

	private IClassHierarchy classHierarchy;

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	public OrderingInference(IClassHierarchy classHierarchy) {
		this.classHierarchy = classHierarchy;
	}

	Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes, IMethod calledMethod)
			throws InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException {
		return inferOrdering(possibleTypes, calledMethod.getElementName());
	}

	private Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes, String calledMethodName)
			throws InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException {
		Ordering ret = null;

		for (TypeAbstraction typeAbstraction : possibleTypes) {
			if (typeAbstraction != TypeAbstraction.TOP) {
				Ordering ordering = inferOrdering(typeAbstraction, calledMethodName);

				if (ret == null)
					ret = ordering;
				else if (ret != ordering) {
					throw new InconsistentPossibleOrderingException(
							"Types have inconsistent orderings, defaulting to ordered: " + possibleTypes);
				}
			}
		}

		return ret;
	}

	Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes) throws InconsistentPossibleOrderingException,
			NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		if (possibleTypes.isEmpty())
			return null;
		else {
			// just process the first one.
			TypeAbstraction typeAbstraction = possibleTypes.iterator().next();
			String methodName = findStreamCreationMethod(typeAbstraction);
			return inferOrdering(possibleTypes, methodName);
		}
	}

	private String findStreamCreationMethod(TypeAbstraction typeAbstraction) {
		Collection<com.ibm.wala.classLoader.IMethod> allMethods = typeAbstraction.getType().getAllMethods();
		for (com.ibm.wala.classLoader.IMethod method : allMethods) {
			TypeReference returnType = method.getReturnType();
			// find the first one that returns a stream.
			if (Util.implementsBaseStream(returnType, this.getClassHierarchy()))
				return method.getName().toString();
		}

		return null;
	}

	// TODO: Cache this?
	private Ordering inferOrdering(String className, String calledMethodName)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		try {
			Class<?> clazz = Class.forName(className);

			// is it instantiable?
			if (!Util.isAbstractType(clazz)) {
				Object instance = createInstance(clazz);
				boolean ordered;

				Spliterator<?> spliterator = getSpliterator(instance, calledMethodName);
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
			LOGGER.log(Level.WARNING, "Can't find: " + className + ". Falling back to: " + Ordering.ORDERED, e);
			return Ordering.ORDERED;
		}
	}

	private Object createInstance(Class<?> clazz) throws NoninstantiableException {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			ObjectInstantiator<?> instantiator = objenesis.getInstantiatorOf(clazz);
			try {
				return instantiator.newInstance();
			} catch (InstantiationError e2) {
				throw new NoninstantiableException(clazz + " cannot be instantiated: " + e2.getCause(), e2, clazz);
			}
		}
	}

	private Spliterator<?> getSpliterator(Object instance, String calledMethodName)
			throws CannotExtractSpliteratorException {
		Objects.requireNonNull(instance);
		Objects.requireNonNull(calledMethodName);

		Spliterator<?> spliterator = null;

		if (instance instanceof Iterable) {
			spliterator = ((Iterable<?>) instance).spliterator();
		} else {
			// try to call the stream() method to get the spliterator.
			BaseStream<?, ?> baseStream = null;
			try {
				Method streamCreationMethod = instance.getClass().getMethod(calledMethodName);
				Object stream = streamCreationMethod.invoke(instance);

				if (stream instanceof BaseStream) {
					baseStream = (BaseStream<?, ?>) stream;
					spliterator = baseStream.spliterator();
				} else
					throw new CannotExtractSpliteratorException(
							"Returned object of type: " + stream.getClass() + " doesn't implement BaseStream.",
							stream.getClass());
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new CannotExtractSpliteratorException(
						"Cannot extract the spliterator from object of type: " + instance.getClass(), e,
						instance.getClass());
			} finally {
				if (baseStream != null)
					baseStream.close();
			}
		}
		return spliterator;
	}

	private Ordering inferOrdering(TypeAbstraction type, String calledMethodName)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		TypeReference typeReference = type.getTypeReference();

		// special case. Arrays are always ordered.
		if (typeReference.isArrayType())
			return Ordering.ORDERED;

		String binaryName = Util.getBinaryName(typeReference);
		return inferOrdering(binaryName, calledMethodName);
	}

	protected IClassHierarchy getClassHierarchy() {
		return classHierarchy;
	}
}
