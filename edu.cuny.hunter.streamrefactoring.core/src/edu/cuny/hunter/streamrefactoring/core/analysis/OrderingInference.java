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
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;

class OrderingInference {

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private IClassHierarchy classHierarchy;

	private Objenesis objenesis = new ObjenesisStd();

	public OrderingInference(IClassHierarchy classHierarchy) {
		this.classHierarchy = classHierarchy;
	}

	private Object createInstance(Class<?> clazz) throws NoninstantiableException {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			ObjectInstantiator<?> instantiator = this.objenesis.getInstantiatorOf(clazz);
			try {
				return instantiator.newInstance();
			} catch (InstantiationError e2) {
				throw new NoninstantiableException(clazz + " cannot be instantiated: " + e2.getCause(), e2, clazz);
			}
		}
	}

	private String findStreamCreationMethod(Collection<TypeAbstraction> types) {
		// find the first one.
		for (TypeAbstraction typeAbstraction : types) {
			String methodName = this.findStreamCreationMethod(typeAbstraction);

			if (methodName != null)
				return methodName;
		}
		// not found.
		return null;
	}

	private String findStreamCreationMethod(IClass type) {
		Collection<com.ibm.wala.classLoader.IMethod> allMethods = type.getAllMethods();
		for (com.ibm.wala.classLoader.IMethod method : allMethods) {
			TypeReference typeToCheck = Util.getEvaluationType(method);

			// find the first one that returns a stream.
			if (Util.implementsBaseStream(typeToCheck, this.getClassHierarchy()))
				return method.getName().toString();
		}

		return null;
	}

	private String findStreamCreationMethod(TypeAbstraction typeAbstraction) {
		IClass type = typeAbstraction.getType();
		return this.findStreamCreationMethod(type);
	}

	protected IClassHierarchy getClassHierarchy() {
		return this.classHierarchy;
	}

	private Spliterator<?> getSpliterator(Object instance, String calledMethodName)
			throws CannotExtractSpliteratorException {
		Objects.requireNonNull(instance);
		Objects.requireNonNull(calledMethodName);

		Spliterator<?> spliterator = null;

		if (instance instanceof Iterable) {
			try {
				spliterator = ((Iterable<?>) instance).spliterator();
			} catch (NullPointerException e) {
				LOGGER.log(Level.WARNING, "Possible trouble creating instance (most likely private type).", e);
				return null;
			}
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

	Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes) throws InconsistentPossibleOrderingException,
			NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		if (possibleTypes.isEmpty())
			return null;
		else {
			String methodName = this.findStreamCreationMethod(possibleTypes);

			if (methodName == null) {
				LOGGER.warning(() -> "Can't find stream creation method for: " + possibleTypes);
				return null;
			}

			return this.inferOrdering(possibleTypes, methodName);
		}
	}

	Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes, IMethod calledMethod)
			throws InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException {
		return this.inferOrdering(possibleTypes, calledMethod.getElementName());
	}

	private Ordering inferOrdering(Collection<TypeAbstraction> possibleTypes, String calledMethodName)
			throws InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException {
		Ordering ret = null;

		for (TypeAbstraction typeAbstraction : possibleTypes) {
			if (typeAbstraction != TypeAbstraction.TOP) {
				Ordering ordering = this.inferOrdering(typeAbstraction, calledMethodName);

				if (ret == null)
					ret = ordering;
				else if (ret != ordering) {
					throw new InconsistentPossibleOrderingException(
							"Types have inconsistent orderings: " + possibleTypes);
				}
			}
		}

		return ret;
	}

	// TODO: Cache this?
	private Ordering inferOrdering(String className, String calledMethodName)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		try {
			Class<?> clazz = Class.forName(className);

			// is it instantiable?
			if (!Util.isAbstractType(clazz)) {
				Object instance = this.createInstance(clazz);
				boolean ordered;

				Spliterator<?> spliterator = this.getSpliterator(instance, calledMethodName);

				if (spliterator == null) {
					LOGGER.warning("Can't extract spliterator. Defaulting to: " + Ordering.ORDERED);
					return Ordering.ORDERED;
				}

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

	private Ordering inferOrdering(TypeAbstraction type, String calledMethodName)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		TypeReference typeReference = type.getTypeReference();

		// special case: arrays are always ordered.
		if (typeReference.isArrayType())
			return Ordering.ORDERED;

		String binaryName = Util.getBinaryName(typeReference);
		return this.inferOrdering(binaryName, calledMethodName);
	}
}
