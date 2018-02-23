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
public class ConvertStreamToParallelRefactoringDescriptor extends JavaRefactoringDescriptor {

	public static final String REFACTORING_ID = "edu.cuny.hunter.streamrefactoring.convert.stream.to.parallel"; //$NON-NLS-1$

	protected ConvertStreamToParallelRefactoringDescriptor() {
		super(REFACTORING_ID);
	}

	public ConvertStreamToParallelRefactoringDescriptor(String project, String description, String comment,
			@SuppressWarnings("rawtypes") Map arguments, int flags) {
		this(REFACTORING_ID, project, description, comment, arguments, flags);
	}

	public ConvertStreamToParallelRefactoringDescriptor(String id, String project, String description, String comment,
			@SuppressWarnings("rawtypes") Map arguments, int flags) {
		super(id, project, description, comment, arguments, flags);
	}

}
