/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.cuny.hunter.streamrefactoring.core.wala;

import java.util.Collection;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.strings.Atom;

/**
 * Modified from AnalysisUtils.java, originally from Keshmesh. Authored by
 * Mohsen Vakilian and Stas Negara. Modified by Nicholas Chen and Raffi
 * Khatchadourian.
 *
 */
public class AnalysisUtils {

	private static final String EXTENSION_CLASSLOADER_NAME = "Extension";

	private static final String OBJECT_GETCLASS_SIGNATURE = "java.lang.Object.getClass()Ljava/lang/Class;"; //$NON-NLS-1$

	public static final String PRIMORDIAL_CLASSLOADER_NAME = "Primordial"; //$NON-NLS-1$

	public static boolean isAnnotatedFactoryMethod(IMethod callee) {
		Collection<Annotation> annotations = callee.getAnnotations();

		if (annotations == null)
			return false;

		for (Annotation annotation : annotations) {
			if (annotation.getType().getName().getClassName().toString().contains("JFlowFactory")) {
				return true;
			}
		}
		return false;
	}

	private static boolean isExtension(Atom classLoaderName) {
		return classLoaderName.toString().equals(EXTENSION_CLASSLOADER_NAME);
	}

	public static boolean isJDKClass(IClass klass) {
		Atom classLoaderName = klass.getClassLoader().getName();
		return isPrimordial(classLoaderName) || isExtension(classLoaderName);
	}

	/**
	 * All projects which the main Eclipse project depends on are in the Extension
	 * loader
	 *
	 * See com.ibm.wala.ide.util.EclipseProjectPath for more information.
	 */
	public static boolean isLibraryClass(IClass klass) {
		return isExtension(klass.getClassLoader().getName());
	}

	public static boolean isLibraryClass(TypeReference typeReference) {
		return isExtension(typeReference.getClassLoader().getName());
	}

	public static boolean isObjectGetClass(IMethod method) {
		return isObjectGetClass(method.getSignature());
	}

	private static boolean isObjectGetClass(String methodSignature) {
		return methodSignature.equals(OBJECT_GETCLASS_SIGNATURE);
	}

	private static boolean isPrimordial(Atom classLoaderName) {
		return classLoaderName.toString().equals(PRIMORDIAL_CLASSLOADER_NAME);
	}

	public static String walaTypeNameToJavaName(TypeName typeName) {
		String fullyQualifiedName = typeName.getPackage() + "." + typeName.getClassName();

		// WALA uses $ to refers to inner classes. We have to replace "$" by "."
		// to make it a valid class name in Java source code.
		return fullyQualifiedName.replace("$", ".").replace("/", ".");
	}
}