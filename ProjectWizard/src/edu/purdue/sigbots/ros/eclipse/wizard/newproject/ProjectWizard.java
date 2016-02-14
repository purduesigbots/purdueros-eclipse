package edu.purdue.sigbots.ros.eclipse.wizard.newproject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

import edu.purdue.sigbots.ros.cli.management.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

public class ProjectWizard extends Wizard implements INewWizard, IExecutableExtension {

	private PROSWizardPage page;
	private IWorkbench workbench;
	private IConfigurationElement config;
	
	public ProjectWizard() {
	}
	
	@Override
	public void addPages() {
		page = new PROSWizardPage("ProjectWizard");
		page.setTitle("VEX Cortex PROS Project");
		page.setDescription("Define the project's kernel version and target environment");
		
		addPage(page);
		setWindowTitle("Create new VEX Cortex PROS Project");
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
	}

	@Override
	public boolean performFinish() {
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProject project = workspace.getRoot().getProject(page.getProjectName());
		if(project != null) {
			final URI projectURI = page.getProjectURI();
			final IProjectDescription projectDescription = workspace.newProjectDescription(project.getName());
			projectDescription.setLocationURI(projectURI);

			final String kernel = page.getKernelTarget();
			Paths.get(project.getFullPath().toOSString());
			final List<String> environments = page.getEnvironmentSelection();
			try {
				IWizardContainer wizardContainer = this.getContainer();
				wizardContainer.run(true, true, new WorkspaceModifyOperation() {
					@Override
					protected void execute(IProgressMonitor monitor)
							throws CoreException, InvocationTargetException {
						try {
							if(monitor.isCanceled()) {
								throw new OperationCanceledException();
							}
							monitor.beginTask("Creating project", 200);
							project.create(projectDescription, new SubProgressMonitor(monitor, 100));
							project.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 100));
							Path path = Paths.get(project.getLocation().toOSString());
							
							PROSActions actions = Activator.getPROSActions();
							actions.createProject(kernel, path, environments);
							monitor.done();
						} catch(IOException e){ 
							throw new InvocationTargetException(e);
						}
					}
				});
				BasicNewProjectResourceWizard.updatePerspective(config);
				BasicNewProjectResourceWizard.selectAndReveal(project, workbench.getActiveWorkbenchWindow());
				return true;
			} catch (InterruptedException ignored) {
			} catch (InvocationTargetException e) {
				Throwable realException = e.getCause();
				MessageDialog.openError(getShell(), "Error",
						realException.getMessage());
			}
		}
		return false;
	}

	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		this.config = config;
		
	}
}
