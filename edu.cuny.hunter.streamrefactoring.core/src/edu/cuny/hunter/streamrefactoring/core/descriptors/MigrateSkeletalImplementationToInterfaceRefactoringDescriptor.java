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
public class MigrateSkeletalImplementationToInterfaceRefactoringDescriptor extends JavaRefactoringDescriptor {

	public static final String REFACTORING_ID = "edu.cuny.hunter.streamrefactoring.migrate.skeletal.implementation.to.interface"; //$NON-NLS-1$

	protected MigrateSkeletalImplementationToInterfaceRefactoringDescriptor() {
		super(REFACTORING_ID);
	}

	public MigrateSkeletalImplementationToInterfaceRefactoringDescriptor(String id, String project, String description,
			String comment, @SuppressWarnings("rawtypes") Map arguments, int flags) {
		super(id, project, description, comment, arguments, flags);
	}

	public MigrateSkeletalImplementationToInterfaceRefactoringDescriptor(String project, String description,
			String comment, @SuppressWarnings("rawtypes") Map arguments, int flags) {
		this(REFACTORING_ID, project, description, comment, arguments, flags);
	}

}
