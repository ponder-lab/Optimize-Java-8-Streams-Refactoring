package edu.cuny.hunter.streamrefactoring.core.contributions;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.internal.corext.refactoring.scripting.JavaUIRefactoringContribution;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import edu.cuny.hunter.streamrefactoring.core.descriptors.MigrateSkeletalImplementationToInterfaceRefactoringDescriptor;

@SuppressWarnings("restriction")
public class MigrateSkeletalImplementationToInterfaceRefactoringContribution
		extends JavaUIRefactoringContribution {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ltk.core.refactoring.RefactoringContribution#createDescriptor
	 * (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
	 * java.util.Map, int)
	 */
	@Override
	public RefactoringDescriptor createDescriptor(String id, String project,
			String description, String comment,
			@SuppressWarnings("rawtypes") Map arguments, int flags)
			throws IllegalArgumentException {
		return new MigrateSkeletalImplementationToInterfaceRefactoringDescriptor(
				id, project, description, comment, arguments, flags);
	}

	@Override
	public Refactoring createRefactoring(JavaRefactoringDescriptor descriptor, RefactoringStatus status)
			throws CoreException {
		return descriptor.createRefactoring(status);
	}
}
