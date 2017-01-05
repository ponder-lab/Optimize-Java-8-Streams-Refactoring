package edu.cuny.hunter.streamrefactoring.core.utils;

import static org.eclipse.jdt.internal.corext.refactoring.structure.TypeVariableUtil.composeMappings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.corext.refactoring.structure.TypeVariableMaplet;

/**
 * TypeVariableUtils customized for use with default method refactoring.
 * 
 * @see {@link org.eclipse.jdt.internal.corext.refactoring.structure.TypeVariableUtil}
 * @author <a href="mailto:rkhatchadourian@citytech.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
@SuppressWarnings("restriction")
public class TypeVariableUtil {
	protected TypeVariableUtil() {
	}

	/**
	 * Returns a type variable mapping from a subclass to a superclass.
	 *
	 * @param subtype
	 *            the type representing the subclass
	 * @param supertype
	 *            the type representing the superclass
	 * @return a type variable mapping. The mapping entries consist of simple
	 *         type variable names.
	 * @throws JavaModelException
	 *             if the signature of one of the types involved could not be
	 *             retrieved
	 */
	public static TypeVariableMaplet[] subTypeToSuperType(final IType subtype, IType implementedInterface,
			final IType supertype) throws JavaModelException {
		Assert.isNotNull(subtype);
		Assert.isNotNull(supertype);

		final TypeVariableMaplet[] mapping = subTypeToInheritedType(subtype, implementedInterface);
		if (mapping.length > 0) {
			final ITypeParameter[] range = supertype.getTypeParameters();
			if (range.length > 0) {
				final String signature = subtype.getSuperclassTypeSignature();
				if (signature != null) {
					final String[] domain = getVariableSignatures(signature);
					if (domain.length > 0)
						return composeMappings(mapping, signaturesToParameters(domain, range));
				}
			}
		}
		return mapping;
	}

	/**
	 * Creates a type variable mapping from a domain to a range.
	 *
	 * @param domain
	 *            the domain of the mapping
	 * @param range
	 *            the range of the mapping
	 * @return a possibly empty type variable mapping
	 */
	private static TypeVariableMaplet[] signaturesToParameters(final String[] domain, final ITypeParameter[] range) {
		Assert.isNotNull(domain);
		Assert.isNotNull(range);

		final List<TypeVariableMaplet> list = new ArrayList<TypeVariableMaplet>();
		String source = null;
		String target = null;
		for (int index = 0; index < Math.min(domain.length, range.length); index++) {
			source = Signature.toString(domain[index]);
			target = range[index].getElementName();
			list.add(new TypeVariableMaplet(source, index, target, index));
		}
		final TypeVariableMaplet[] result = new TypeVariableMaplet[list.size()];
		list.toArray(result);
		return result;
	}

	/**
	 * Returns a type variable mapping from a subclass to a superclass.
	 *
	 * @param type
	 *            the type representing the subclass class
	 * @return a type variable mapping. The mapping entries consist of simple
	 *         type variable names.
	 * @throws JavaModelException
	 *             if the signature of one of the types involved could not be
	 *             retrieved
	 */
	public static TypeVariableMaplet[] subTypeToInheritedType(final IType type, IType implementedInterface)
			throws JavaModelException {
		Assert.isNotNull(type);

		final ITypeParameter[] domain = type.getTypeParameters();
		if (domain.length > 0) {
			String fullyQualifiedParameterizedName = implementedInterface.getFullyQualifiedParameterizedName();

			// RK: strip off bounds if present. Otherwise, createTypeSignature()
			// won't work.
			fullyQualifiedParameterizedName = stripBoundsFromFullyQualifiedParameterizedName(
					fullyQualifiedParameterizedName);

			String signature = null;
			try {
				signature = Signature.createTypeSignature(fullyQualifiedParameterizedName,
						implementedInterface.isResolved());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Could not create type signature for: "
						+ implementedInterface.getFullyQualifiedParameterizedName(), e);
			}

			if (signature != null) {
				final String[] range = getVariableSignatures(signature);
				if (range.length > 0)
					return parametersToSignatures(domain, range, true);
			}
		}
		return new TypeVariableMaplet[0];
	}

	protected static String stripBoundsFromFullyQualifiedParameterizedName(String fullyQualifiedParameterizedName) {
		// look for extends.
		int startingIndexOfUpperBoundClause = fullyQualifiedParameterizedName.indexOf("extends");

		// look for super.
		int startingIndexOfLowerBoundClause = fullyQualifiedParameterizedName.indexOf("super");

		// find the starting index of the first bounds clause.
		int startingIndexOfBoundClause;

		if (startingIndexOfUpperBoundClause == -1 && startingIndexOfLowerBoundClause == -1)
			return fullyQualifiedParameterizedName; // not found.
		else if (startingIndexOfUpperBoundClause != -1 && startingIndexOfLowerBoundClause == -1)
			startingIndexOfBoundClause = startingIndexOfUpperBoundClause; // extends
																			// is
																			// first.
		else if (startingIndexOfUpperBoundClause == -1 && startingIndexOfLowerBoundClause != -1)
			startingIndexOfBoundClause = startingIndexOfLowerBoundClause; // super
																			// is
																			// first.
		else // pick the one that appears first.
			startingIndexOfBoundClause = Math.min(startingIndexOfLowerBoundClause, startingIndexOfUpperBoundClause);

		int endingIndexOfBoundClause = findEndingIndexOfBoundClause(fullyQualifiedParameterizedName,
				startingIndexOfBoundClause);

		if (endingIndexOfBoundClause == -1)
			throw new IllegalArgumentException(fullyQualifiedParameterizedName + " is not well-formed.");

		// delete the clause.
		StringBuilder ret = new StringBuilder(fullyQualifiedParameterizedName).delete(startingIndexOfBoundClause,
				endingIndexOfBoundClause);

		// also remove any leading whitespace from where the clause was.
		for (int i = startingIndexOfBoundClause - 1; i >= 0; i--) {
			if (Character.isWhitespace(ret.charAt(i)))
				ret.deleteCharAt(i);
			else
				break;
		}

		return stripBoundsFromFullyQualifiedParameterizedName(ret.toString());
	}

	protected static int findEndingIndexOfBoundClause(String fullyQualifiedParameterizedName,
			int startingIndexOfBoundClause) {
		int bracketCount = 1;
		for (int i = startingIndexOfBoundClause; i < fullyQualifiedParameterizedName.length(); i++) {
			char curr = fullyQualifiedParameterizedName.charAt(i);

			if (curr == '<')
				bracketCount++;
			else if (curr == '>')
				bracketCount--;
			else if (curr == ',' && bracketCount == 1)
				return i;

			if (bracketCount == 0)
				return i;
		}
		return -1; // not found.
	}

	/**
	 * Returns the type variable signatures of the specified parameterized type
	 * signature, or an empty array if none.
	 *
	 * @param signature
	 *            the signature to get its type variable signatures from. The
	 *            signature must be a parameterized type signature.
	 * @return a possibly empty array of type variable signatures
	 * @see Signature#getTypeArguments(String)
	 */
	private static String[] getVariableSignatures(final String signature) {
		Assert.isNotNull(signature);

		String[] result = null;
		try {
			result = Signature.getTypeArguments(signature);
		} catch (IllegalArgumentException exception) {
			result = new String[0];
		}
		return result;
	}

	/**
	 * Creates a type variable mapping from a domain to a range.
	 *
	 * @param domain
	 *            the domain of the mapping
	 * @param range
	 *            the range of the mapping
	 * @param indexes
	 *            <code>true</code> if the indexes should be compared,
	 *            <code>false</code> if the names should be compared
	 * @return a possibly empty type variable mapping
	 */
	private static TypeVariableMaplet[] parametersToSignatures(final ITypeParameter[] domain, final String[] range,
			final boolean indexes) {
		Assert.isNotNull(domain);
		Assert.isNotNull(range);

		final Set<TypeVariableMaplet> set = new HashSet<TypeVariableMaplet>();
		ITypeParameter source = null;
		String target = null;
		String element = null;
		String signature = null;
		for (int index = 0; index < domain.length; index++) {
			source = domain[index];
			for (int offset = 0; offset < range.length; offset++) {
				target = range[offset];
				element = source.getElementName();
				signature = Signature.toString(target);
				if (indexes) {
					if (offset == index)
						set.add(new TypeVariableMaplet(element, index, signature, offset));
				} else {
					if (element.equals(signature))
						set.add(new TypeVariableMaplet(element, index, signature, offset));
				}
			}
		}
		final TypeVariableMaplet[] result = new TypeVariableMaplet[set.size()];
		set.toArray(result);
		return result;
	}

	/**
	 * Returns all type variable names of the indicated member not mapped by the
	 * specified type variable mapping.
	 *
	 * @param mapping
	 *            the type variable mapping. The entries of this mapping must be
	 *            simple type variable names.
	 * @param declaring
	 *            the declaring type of the indicated member
	 * @param member
	 *            the member to determine its unmapped type variable names
	 * @return a possibly empty array of unmapped type variable names
	 * @throws JavaModelException
	 *             if the type parameters of the member could not be determined
	 */
	public static String[] getUnmappedVariables(final TypeVariableMaplet[] mapping, final IType declaring,
			final IMember member) throws JavaModelException {
		return org.eclipse.jdt.internal.corext.refactoring.structure.TypeVariableUtil.getUnmappedVariables(mapping,
				declaring, member);
	}
}
