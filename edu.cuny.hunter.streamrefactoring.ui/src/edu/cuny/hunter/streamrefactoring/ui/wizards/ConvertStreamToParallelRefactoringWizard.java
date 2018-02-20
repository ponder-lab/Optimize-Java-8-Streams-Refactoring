/**
 * 
 */
package edu.cuny.hunter.streamrefactoring.ui.wizards;

import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringStarter;
import org.eclipse.jdt.ui.refactoring.RefactoringSaveHelper;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.refactorings.ConvertToParallelStreamRefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
public class ConvertStreamToParallelRefactoringWizard extends RefactoringWizard {

	public ConvertStreamToParallelRefactoringWizard(Refactoring refactoring) {
		super(refactoring,
				RefactoringWizard.DIALOG_BASED_USER_INTERFACE & RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN);
		this.setWindowTitle(Messages.Name);
	}

	public static void startRefactoring(IJavaProject[] javaProjects, Shell shell, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		// TODO: Will need to set the target type at some point but see #23.
		Refactoring refactoring = Util.createRefactoring(javaProjects, monitor);
		RefactoringWizard wizard = new ConvertStreamToParallelRefactoringWizard(refactoring);

		new RefactoringStarter().activate(wizard, shell, RefactoringMessages.OpenRefactoringWizardAction_refactoring,
				RefactoringSaveHelper.SAVE_REFACTORING);
	}

	// TODO: Need to add an option for N here #183.
	@Override
	protected void addUserInputPages() {
	}
}
