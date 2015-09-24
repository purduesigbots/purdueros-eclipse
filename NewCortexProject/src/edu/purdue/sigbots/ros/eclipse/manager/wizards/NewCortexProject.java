package edu.purdue.sigbots.ros.eclipse.manager.wizards;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

import edu.purdue.sigbots.ros.eclipse.manager.CmdLineUpdate;

/**
 * Implements a "File > New > VEX Cortex PROS Project" wizard!
 */
public class NewCortexProject extends Wizard implements INewWizard, IExecutableExtension {
	/**
	 * Adds a new file to the project with template replacement of "Default_VeX_Cortex"
	 * with the correct project name.
	 */
	private static void addTemplateFile(IContainer container, Path path,
			InputStream contentStream, IProgressMonitor monitor, String projectName)
			throws CoreException {
		final ByteArrayOutputStream os = new ByteArrayOutputStream(16384);
		/* Transfer contents */
		try {
			final BufferedReader br = new BufferedReader(new InputStreamReader(contentStream));
			final PrintWriter out = new PrintWriter(new OutputStreamWriter(os));
			String line;
			while ((line = br.readLine()) != null)
				out.println(line.replaceAll("Default_VeX_Cortex", projectName));
			out.close();
			br.close();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, "Error", e.getMessage(), e));
		}
		final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
		final IFile file = container.getFile(path);
		if (file.exists())
			file.setContents(is, true, true, monitor);
		else
			file.create(is, true, monitor);
	}

	/**
	 * Adds a new file to the project.
	 */
	public static void addFileToProject(IContainer container, Path path,
			InputStream contentStream, IProgressMonitor monitor)
			throws CoreException {
		final IFile file = container.getFile(path);
		if (file.exists())
			file.setContents(contentStream, true, true, monitor);
		else
			file.create(contentStream, true, monitor);
		try {
			contentStream.close();
		} catch (IOException ignore) { }
	}

	private static Path createPath(IFolder parent, String child) {
		return new Path(parent.getName() + Path.SEPARATOR + child);
	}

	/**
	 * This creates the project in the workspace.
	 * 
	 * @param description
	 * @param projectHandle
	 * @param monitor
	 */
	private static void createProject(IProjectDescription description, IProject proj,
			IProgressMonitor monitor) throws CoreException, OperationCanceledException, FileNotFoundException {
		try {
			monitor.beginTask("Creating project", 200);
			proj.create(description, new SubProgressMonitor(monitor, 100));
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			proj.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 100));
			IContainer container = (IContainer) proj;
			/* Add makefiles */
			addFileToProject(container, new Path("Makefile"),
					CmdLineUpdate.openStream("Makefile"), monitor);
			addFileToProject(container, new Path("common.mk"),
				CmdLineUpdate.openStream("common.mk"), monitor);
			addTemplateFile(container, new Path(".project"),
					CmdLineUpdate.openStream(".project"), monitor, proj.getName());
			addFileToProject(container, new Path(".cproject"),
				CmdLineUpdate.openStream(".cproject"), monitor);
			
			/* Add the firmware, include, src folders */
			final IFolder fwFolder = container.getFolder(new Path("firmware"));
			fwFolder.create(true, true, monitor);
			addFileToProject(container, createPath(fwFolder, "cortex.ld"),
					CmdLineUpdate.openStream("firmware/cortex.ld"), monitor);
			addFileToProject(container, createPath(fwFolder, "STM32F10x.ld"),
				CmdLineUpdate.openStream("firmware/STM32F10x.ld"), monitor);
			addFileToProject(container, createPath(fwFolder, "libccos.a"),
				CmdLineUpdate.openStream("firmware/libccos.a"), monitor);
			addFileToProject(container, createPath(fwFolder, "uniflash.jar"),
				CmdLineUpdate.openStream("firmware/uniflash.jar"), monitor);
			final IFolder incFolder = container.getFolder(new Path("include"));
			incFolder.create(true, true, monitor);
			addFileToProject(container, createPath(incFolder, "API.h"),
				CmdLineUpdate.openStream("include/API.h"), monitor);
			addFileToProject(container, createPath(incFolder, "main.h"),
				CmdLineUpdate.openStream("include/main.h"), monitor);
			final IFolder srcFolder = container.getFolder(new Path("src"));
			srcFolder.create(true, true, monitor);
			addFileToProject(container, createPath(srcFolder, "auto.c"),
				CmdLineUpdate.openStream("src/auto.c"), monitor);
			addFileToProject(container, createPath(srcFolder, "opcontrol.c"),
				CmdLineUpdate.openStream("src/opcontrol.c"), monitor);
			addFileToProject(container, createPath(srcFolder, "init.c"),
				CmdLineUpdate.openStream("src/init.c"), monitor);
			addFileToProject(container, createPath(srcFolder, "Makefile"),
				CmdLineUpdate.openStream("src/Makefile"), monitor);
		} catch (OperationCanceledException ignore) {
			/* Swallow a cancel gracefully */
		} catch (CoreException e) {
			throw e;
		} catch (NullPointerException e) {
			throw e;
		} catch (FileNotFoundException e) {
			throw e;
		} finally {
			monitor.done();
		}
	}

	private IConfigurationElement config;
	private WizardNewProjectCreationPage page;
	private IWorkbench workbench;

	/**
	 * Constructor for NewCortexProject.
	 */
	public NewCortexProject() {
		super();
		setNeedsProgressMonitor(true);
	}

	/**
	 * Adding the page to the wizard.
	 */
	public void addPages() {
		page = new WizardNewProjectCreationPage("NewCortexProject");
		page.setDescription("Enter project name");
		page.setTitle("Create a PROS Project");
		addPage(page);
		setWindowTitle("PROS Project");
	}

	/**
	 * This method is called when 'Finish' button is pressed in the wizard. We
	 * will create an operation and run it using wizard as execution context.
	 */
	public boolean performFinish() {
		final IProject projectHandle = page.getProjectHandle();
		if (projectHandle != null) {
			final URI projectURI = (!page.useDefaults()) ? page.getLocationURI() : null;
			final IWorkspace workspace = ResourcesPlugin.getWorkspace();
			final IProjectDescription desc = workspace.newProjectDescription(
				projectHandle.getName());
			desc.setLocationURI(projectURI);
			try {
				IWizardContainer containter = getContainer();
				containter.run(true, true, new WorkspaceModifyOperation() { 
					protected void execute(IProgressMonitor monitor) throws CoreException, InvocationTargetException {
						try {
							createProject(desc, projectHandle, monitor);
						} catch (FileNotFoundException e) {
							throw new InvocationTargetException(e);
						}
					}
				});
				BasicNewProjectResourceWizard.updatePerspective(config);
				BasicNewProjectResourceWizard.selectAndReveal(projectHandle,
					workbench.getActiveWorkbenchWindow());
				return true;
			} catch (InterruptedException e) {
			} catch (InvocationTargetException e) {
				Throwable realException = e.getTargetException();
				MessageDialog.openError(getShell(), "Error",
						realException.getMessage());
			}
		}
		return false;
	}

	/**
	 * Sets the initialization data for the wizard.
	 */
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException {
		this.config = config;
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
	}
}