package edu.cuny.hunter.streamrefactoring.eval.utils;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class Util {
	public static String getMethodIdentifier(IMethod method) throws JavaModelException {
		if (method == null)
			return null;

		StringBuilder sb = new StringBuilder();
		sb.append(method.getElementName() + "(");
		ILocalVariable[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			sb.append(edu.cuny.hunter.streamrefactoring.core.utils.Util
					.getQualifiedNameFromTypeSignature(parameters[i].getTypeSignature(), method.getDeclaringType()));
			if (i != parameters.length - 1)
				sb.append(",");
		}
		sb.append(")");
		return sb.toString();
	}

	public static IJavaProject[] getSelectedJavaProjectsFromEvent(ExecutionEvent event) throws ExecutionException {
		ISelection currentSelection = HandlerUtil.getCurrentSelectionChecked(event);

		List<?> list = SelectionUtil.toList(currentSelection);
		IJavaProject[] javaProjects = list.stream().filter(e -> e instanceof IJavaProject)
				.toArray(length -> new IJavaProject[length]);
		return javaProjects;
	}

	private Util() {
	}
}
