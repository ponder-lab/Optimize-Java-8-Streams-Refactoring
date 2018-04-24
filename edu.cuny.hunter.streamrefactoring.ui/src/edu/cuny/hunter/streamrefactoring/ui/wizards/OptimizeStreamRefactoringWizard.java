/**
 *
 */
package edu.cuny.hunter.streamrefactoring.ui.wizards;

import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.swt.widgets.Shell;

import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
public class OptimizeStreamRefactoringWizard extends RefactoringWizard {

	public static void startRefactoring(IJavaProject[] javaProjects, Shell shell, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Refactoring refactoring = Util.createRefactoring(javaProjects, monitor);
		RefactoringWizard wizard = new OptimizeStreamRefactoringWizard(refactoring);

		new RefactoringStarter().activate(wizard, shell, RefactoringMessages.OpenRefactoringWizardAction_refactoring,
				RefactoringSaveHelper.SAVE_REFACTORING);
	}

	public OptimizeStreamRefactoringWizard(Refactoring refactoring) {
		super(refactoring,
				RefactoringWizard.DIALOG_BASED_USER_INTERFACE & RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN);
		this.setWindowTitle(Messages.Name);
	}

	// TODO: Need to add an option for N here #183.
	@Override
	protected void addUserInputPages() {
	}
}
