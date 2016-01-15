package edu.purdue.sigbots.ros.eclipse.wizard.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

import edu.purdue.sigbots.ros.cli.updater.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

public class UpgradeProjectWizard extends Wizard implements IWizard, IExecutableExtension {
	
	private UpgradeProjectPage page;
	private IConfigurationElement config;
	
	public UpgradeProjectWizard() {
	}

	@Override
	public boolean performFinish() {
		final IProject project = page.getProject();
		if(project != null) {

			final String kernel = page.getKernelTarget();
			final List<String> environments = page.getEnvironments();
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
							monitor.beginTask("Upgrading project", 200);
							project.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 100));
							Path path = Paths.get(project.getLocation().toOSString());
		
							PROSActions actions = Activator.getPROSActions();
							actions.upgradeProject(kernel, path, environments);
							project.refreshLocal(IResource.DEPTH_INFINITE, new SubProgressMonitor(monitor, 100));
							monitor.done();
						} catch(IOException e){ 
							throw new InvocationTargetException(e);
						}
					}
				});
				BasicNewProjectResourceWizard.updatePerspective(config);
				BasicNewProjectResourceWizard.selectAndReveal(project, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
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
	public void createPageControls(Composite pageContainer) {
		page = new UpgradeProjectPage("UpgradeProjectPage");
		page.setTitle("Upgrade PROS Project");
		page.setDescription("Upgrade a project to a newer version of PROS");
		
		addPage(page);
		setWindowTitle("Upgrade PROS Project");
	}

	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data)
			throws CoreException {
		this.config = config;
	}
}
