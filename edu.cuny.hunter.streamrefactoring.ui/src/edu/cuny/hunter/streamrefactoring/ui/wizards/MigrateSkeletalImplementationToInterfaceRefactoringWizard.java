/**
 * 
 */
package edu.cuny.hunter.streamrefactoring.ui.wizards;

import java.util.Optional;

import org.eclipse.core.runtime.IProgressMonitor;
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
import edu.cuny.hunter.streamrefactoring.core.refactorings.MigrateSkeletalImplementationToInterfaceRefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

/**
 * @author <a href="mailto:rkhatchadourian@citytech.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
public class MigrateSkeletalImplementationToInterfaceRefactoringWizard extends RefactoringWizard {

	public MigrateSkeletalImplementationToInterfaceRefactoringWizard(Refactoring refactoring) {
		super(refactoring,
				RefactoringWizard.DIALOG_BASED_USER_INTERFACE & RefactoringWizard.CHECK_INITIAL_CONDITIONS_ON_OPEN);
		this.setWindowTitle(Messages.Name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ltk.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	@Override
	protected void addUserInputPages() {
		addPage(new MigrateSkeletalImplementationToInterfaceInputPage());
	}

	private static class MigrateSkeletalImplementationToInterfaceInputPage extends UserInputWizardPage {

		public static final String PAGE_NAME = "MigrateSkeletalImplementationToInterfaceInputPage"; //$NON-NLS-1$

		private static final String DESCRIPTION = Messages.Name;

		private static final String DIALOG_SETTING_SECTION = "MigrateSkeletalImplementationToInterface"; //$NON-NLS-1$
		private static final String DEPRECATE_EMPTY_DECLARING_TYPES = "deprecateEmptyDeclaringTypes"; //$NON-NLS-1$
		private static final String CONSIDER_NONSTANDARD_ANNOTATION_DIFFERENCES = "considerNonstandardAnnotationDifferences"; //$NON-NLS-1$

		IDialogSettings settings;

		private MigrateSkeletalImplementationToInterfaceRefactoringProcessor processor;

		public MigrateSkeletalImplementationToInterfaceInputPage() {
			super(PAGE_NAME);
			setDescription(DESCRIPTION);
		}

		@Override
		public void createControl(Composite parent) {
			ProcessorBasedRefactoring processorBasedRefactoring = (ProcessorBasedRefactoring) getRefactoring();
			RefactoringProcessor refactoringProcessor = processorBasedRefactoring.getProcessor();
			this.processor = (MigrateSkeletalImplementationToInterfaceRefactoringProcessor) refactoringProcessor;
			this.loadSettings();

			Composite result = new Composite(parent, SWT.NONE);
			setControl(result);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			result.setLayout(layout);

			Label doit = new Label(result, SWT.WRAP);
			doit.setText("Migrate instance methods in classes to interfaces as default methods.");
			doit.setLayoutData(new GridData());

			Label separator = new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

			Button cloneCheckBox = new Button(result, SWT.CHECK);
			cloneCheckBox.setText("Deprecate empty declaring types rather than removing them.");
			boolean deprecateDeclaringTypesValue = settings.getBoolean(DEPRECATE_EMPTY_DECLARING_TYPES);
			this.processor.setDeprecateEmptyDeclaringTypes(deprecateDeclaringTypesValue);
			cloneCheckBox.setSelection(deprecateDeclaringTypesValue);
			cloneCheckBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setDeprecateEmptyDeclaringTypes(((Button) e.widget).getSelection());
				}
			});

			Button leaveRawCheckBox = new Button(result, SWT.CHECK);
			leaveRawCheckBox.setText("Analyze differences in nonstandard annotation types.");
			boolean noAnnotationsValue = settings.getBoolean(CONSIDER_NONSTANDARD_ANNOTATION_DIFFERENCES);
			processor.setConsiderNonstandardAnnotationDifferences(noAnnotationsValue);
			leaveRawCheckBox.setSelection(noAnnotationsValue);
			leaveRawCheckBox.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					setConsiderNonstandardAnnotationDifferences(((Button) e.widget).getSelection());
				}
			});

			updateStatus();
			Dialog.applyDialogFont(result);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(),
					"migrate_skeletal_implementation_to_interface_wizard_page_context");
		}

		private void setDeprecateEmptyDeclaringTypes(boolean selection) {
			settings.put(DEPRECATE_EMPTY_DECLARING_TYPES, selection);
			this.processor.setDeprecateEmptyDeclaringTypes(selection);
		}

		private void setConsiderNonstandardAnnotationDifferences(boolean selection) {
			settings.put(CONSIDER_NONSTANDARD_ANNOTATION_DIFFERENCES, selection);
			processor.setConsiderNonstandardAnnotationDifferences(selection);
		}

		private void updateStatus() {
			setPageComplete(true);
		}

		private void loadSettings() {
			settings = getDialogSettings().getSection(DIALOG_SETTING_SECTION);
			if (settings == null) {
				settings = getDialogSettings().addNewSection(DIALOG_SETTING_SECTION);
				settings.put(DEPRECATE_EMPTY_DECLARING_TYPES, false);
				settings.put(CONSIDER_NONSTANDARD_ANNOTATION_DIFFERENCES, true);
			}
			processor.setDeprecateEmptyDeclaringTypes(settings.getBoolean(DEPRECATE_EMPTY_DECLARING_TYPES));
			processor.setConsiderNonstandardAnnotationDifferences(
					settings.getBoolean(CONSIDER_NONSTANDARD_ANNOTATION_DIFFERENCES));
		}

	}

	public static void startRefactoring(IMethod[] methods, Shell shell, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		// TODO: Will need to set the target type at some point but see #23.
		Refactoring refactoring = Util.createRefactoring(methods, monitor);
		RefactoringWizard wizard = new MigrateSkeletalImplementationToInterfaceRefactoringWizard(refactoring);

		new RefactoringStarter().activate(wizard, shell, RefactoringMessages.OpenRefactoringWizardAction_refactoring,
				RefactoringSaveHelper.SAVE_REFACTORING);
	}
}
