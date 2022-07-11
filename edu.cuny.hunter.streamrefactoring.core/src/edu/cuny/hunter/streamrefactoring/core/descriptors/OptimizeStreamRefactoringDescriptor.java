/**
 *
 */
package edu.cuny.hunter.streamrefactoring.core.descriptors;

import java.util.Map;

import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;

/**
 * @author raffi
 *
 */
public class OptimizeStreamRefactoringDescriptor extends JavaRefactoringDescriptor {

	public static final String REFACTORING_ID = "edu.cuny.hunter.streamrefactoring.optimize.stream"; //$NON-NLS-1$

	protected OptimizeStreamRefactoringDescriptor() {
		super(REFACTORING_ID);
	}

	public OptimizeStreamRefactoringDescriptor(String project, String description, String comment,
			@SuppressWarnings("rawtypes") Map arguments, int flags) {
		this(REFACTORING_ID, project, description, comment, arguments, flags);
	}

	public OptimizeStreamRefactoringDescriptor(String id, String project, String description, String comment,
			@SuppressWarnings("rawtypes") Map arguments, int flags) {
		super(id, project, description, comment, arguments, flags);
	}
}
