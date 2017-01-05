package edu.cuny.hunter.streamrefactoring.core.refactorings;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.IMethod;

class UnmigratableMethodSet extends LinkedHashSet<IMethod> {

	private static final long serialVersionUID = -2882770464986650890L;

	protected Set<IMethod> sourceMethods;

	public UnmigratableMethodSet(Set<IMethod> sourceMethods) {
		super();
		this.sourceMethods = sourceMethods;
	}

	public UnmigratableMethodSet(Collection<? extends IMethod> c, Set<IMethod> sourceMethods) {
		super(c);
		this.sourceMethods = sourceMethods;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.HashSet#add(java.lang.Object)
	 */
	@Override
	public boolean add(IMethod e) {
		if (this.getSourceMethods().contains(e))
			return super.add(e);
		else
			throw new IllegalArgumentException(
					"Method: " + e.getElementName() + " is not contained in the source set.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.AbstractCollection#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends IMethod> c) {
		if (this.sourceMethods.containsAll(c))
			return super.addAll(c);
		else
			throw new IllegalArgumentException(
					"Collection: " + c + " has methods not contained in the source method set.");
	}

	public UnmigratableMethodSet(int initialCapacity, float loadFactor, Set<IMethod> sourceMethods) {
		super(initialCapacity, loadFactor);
		this.sourceMethods = sourceMethods;
	}

	public UnmigratableMethodSet(int initialCapacity, Set<IMethod> sourceMethods) {
		super(initialCapacity);
		this.sourceMethods = sourceMethods;
	}

	/**
	 * Creates a new Unmigratable method set with the given source methods.
	 * 
	 * @param sourceMethods
	 *            The source methods, some of which may be unmigratable.
	 */
	public void setSourceMethods(Set<IMethod> sourceMethods) {
		this.sourceMethods = sourceMethods;
	}

	/**
	 * @return the sourceMethods
	 */
	protected Set<IMethod> getSourceMethods() {
		return sourceMethods;
	}
}
