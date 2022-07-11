/**
 *
 */
package edu.cuny.hunter.streamrefactoring.ui.wizards;

import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
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
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.refactorings.OptimizeStreamsRefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 */
public class OptimizeStreamRefactoringWizard extends RefactoringWizard {

	private static class OptimizeStreamsInputPage extends UserInputWizardPage {

		private static final String DESCRIPTION = Messages.Name;

		private static final String DIALOG_SETTING_SECTION = "OptimizeStreams"; //$NON-NLS-1$

		private static final String K_FOR_STREAMS = "kForStreams"; //$NON-NLS-1$

		public static final String PAGE_NAME = "OptimizeStreamsInputPage"; //$NON-NLS-1$

		private static final String USE_IMPLICIT_ENTRY_POINTS = "useImplicitEntryPoints"; //$NON-NLS-1$

		private static final String USE_IMPLICIT_JAVAFX_ENTRY_POINTS = "useImplicitJavaFXEntryPoints"; //$NON-NLS-1$

		private static final String USE_IMPLICIT_JMH_ENTRY_POINTS = "useImplicitJMHEntryPoints"; //$NON-NLS-1$

		private static final String USE_IMPLICIT_TEST_ENTRY_POINTS = "useImplicitTestEntryPoints"; //$NON-NLS-1$

		private OptimizeStreamsRefactoringProcessor processor;

		IDialogSettings settings;

		public OptimizeStreamsInputPage() {
			super(PAGE_NAME);
			this.setDescription(DESCRIPTION);
		}

		private void addBooleanButton(String text, String key, Consumer<Boolean> valueConsumer, Composite result) {
			Button button = new Button(result, SWT.CHECK);
			button.setText(text);
			boolean value = this.settings.getBoolean(key);
			valueConsumer.accept(value);
			button.setSelection(value);
			button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean selection = ((Button) e.widget).getSelection();
					OptimizeStreamsInputPage.this.settings.put(key, selection);
					valueConsumer.accept(selection);
				}
			});
		}

		private void addIntegerButton(String text, String key, Consumer<Integer> valueConsumer, Composite result) {
			Label label = new Label(result, SWT.HORIZONTAL);
			label.setText(text);

			Text textBox = new Text(result, SWT.SINGLE);
			int value = this.settings.getInt(key);
			valueConsumer.accept(value);
			textBox.setText(String.valueOf(value));
			textBox.addModifyListener(event -> {
				int selection;
				try {
					selection = Integer.parseInt(((Text) event.widget).getText());
				} catch (NumberFormatException e) {
					return;
				}
				OptimizeStreamsInputPage.this.settings.put(key, selection);
				valueConsumer.accept(selection);
			});
		}

		@Override
		public void createControl(Composite parent) {
			ProcessorBasedRefactoring processorBasedRefactoring = (ProcessorBasedRefactoring) this.getRefactoring();
			RefactoringProcessor refactoringProcessor = processorBasedRefactoring.getProcessor();
			this.setProcessor((OptimizeStreamsRefactoringProcessor) refactoringProcessor);
			this.loadSettings();

			Composite result = new Composite(parent, SWT.NONE);
			this.setControl(result);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			result.setLayout(layout);

			Label doit = new Label(result, SWT.WRAP);
			doit.setText("Optimize Java 8 Streams for increased parallelism and efficiency.");
			doit.setLayoutData(new GridData());

			Label separator = new Label(result, SWT.SEPARATOR | SWT.HORIZONTAL);
			separator.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

			// set up buttons.
			this.addBooleanButton("Automatically discover standard entry points (main methods).",
					USE_IMPLICIT_ENTRY_POINTS, this.getProcessor()::setUseImplicitEntrypoints, result);
			this.addBooleanButton("Automatically discover test-based entry points (JUnit).",
					USE_IMPLICIT_TEST_ENTRY_POINTS, this.getProcessor()::setUseImplicitTestEntrypoints, result);
			this.addBooleanButton("Automatically discover benchmark entry points (JMH).", USE_IMPLICIT_JMH_ENTRY_POINTS,
					this.getProcessor()::setUseImplicitBenchmarkEntrypoints, result);
			this.addBooleanButton("Automatically discover GUI entry points (JavaFX).", USE_IMPLICIT_JAVAFX_ENTRY_POINTS,
					this.getProcessor()::setUseImplicitJavaFXEntrypoints, result);

			Composite compositeForIntegerButton = new Composite(result, SWT.NONE);
			GridLayout layoutForIntegerButton = new GridLayout(2, false);

			compositeForIntegerButton.setLayout(layoutForIntegerButton);

			this.addIntegerButton("k value to use for streams for kCFA: ", K_FOR_STREAMS,
					this.getProcessor()::setNForStreams, compositeForIntegerButton);

			this.updateStatus();
			Dialog.applyDialogFont(result);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this.getControl(),
					"optimize_streams_wizard_page_context");
		}

		private OptimizeStreamsRefactoringProcessor getProcessor() {
			return this.processor;
		}

		private void loadSettings() {
			this.settings = this.getDialogSettings().getSection(DIALOG_SETTING_SECTION);
			if (this.settings == null) {
				this.settings = this.getDialogSettings().addNewSection(DIALOG_SETTING_SECTION);
				this.settings.put(USE_IMPLICIT_ENTRY_POINTS, this.getProcessor().getUseImplicitEntrypoints());
				this.settings.put(USE_IMPLICIT_TEST_ENTRY_POINTS, this.getProcessor().getUseImplicitTestEntrypoints());
				this.settings.put(USE_IMPLICIT_JMH_ENTRY_POINTS,
						this.getProcessor().getUseImplicitBenchmarkEntrypoints());
				this.settings.put(USE_IMPLICIT_JAVAFX_ENTRY_POINTS,
						this.getProcessor().getUseImplicitBenchmarkEntrypoints());
				this.settings.put(K_FOR_STREAMS, this.getProcessor().getNForStreams());
			}
			this.processor.setUseImplicitEntrypoints(this.settings.getBoolean(USE_IMPLICIT_ENTRY_POINTS));
			this.processor.setUseImplicitTestEntrypoints(this.settings.getBoolean(USE_IMPLICIT_TEST_ENTRY_POINTS));
			this.processor.setUseImplicitBenchmarkEntrypoints(this.settings.getBoolean(USE_IMPLICIT_JMH_ENTRY_POINTS));
			this.processor.setUseImplicitJavaFXEntrypoints(this.settings.getBoolean(USE_IMPLICIT_JAVAFX_ENTRY_POINTS));

			int value;
			try {
				value = this.settings.getInt(K_FOR_STREAMS);
			} catch (NumberFormatException e) {
				this.settings.put(K_FOR_STREAMS, this.getProcessor().getNForStreams());
				value = this.settings.getInt(K_FOR_STREAMS);
			}
			this.processor.setNForStreams(value);
		}

		private void setProcessor(OptimizeStreamsRefactoringProcessor processor) {
			this.processor = processor;
		}

		private void updateStatus() {
			this.setPageComplete(true);
		}
	}

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

	@Override
	protected void addUserInputPages() {
		this.addPage(new OptimizeStreamsInputPage());
	}
}
