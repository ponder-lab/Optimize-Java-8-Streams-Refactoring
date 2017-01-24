package edu.cuny.hunter.streamrefactoring.core.wala;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.util.collections.MapUtil;

public class TestableJavaEclipseProjectPath extends JavaEclipseProjectPath {

	public TestableJavaEclipseProjectPath(EclipseProjectPath.AnalysisScopeType scopeType)
			throws IOException, CoreException {
		super(scopeType);
	}

	@Override
	protected void resolveLibraryPathEntry(EclipseProjectPath.ILoader loader, IPath p) {
		File file = makeAbsolute(p).toFile();
		JarFile j;
		try {
			j = new JarFile(file);
		} catch (ZipException z) {
			// a corrupted file. ignore it.
			return;
		} catch (IOException z) {
			// should ignore directories as well..
			return;
		}
		if (isPrimordialJarFile(j)) {
			// force it.
			loader = Loader.PRIMORDIAL;
		}
		List<Module> s = MapUtil.findOrCreateList(modules, loader);
		s.add(file.isDirectory() ? (Module) new BinaryDirectoryTreeModule(file) : (Module) new JarFileModule(j));
	}

	@Override
	protected boolean isPrimordialJarFile(JarFile j) {
		return j.getName().endsWith("rtstubs18.jar");
	}

	public static EclipseProjectPath<?, IJavaProject> create(IJavaProject project, AnalysisScopeType scopeType)
			throws IOException, CoreException {
		TestableJavaEclipseProjectPath path = new TestableJavaEclipseProjectPath(scopeType);
		path.create(project.getProject());
		return path;
	}
}
