package edu.purdue.sigbots.ros.eclipse.wizard.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import edu.purdue.sigbots.ros.cli.management.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

public class UpgradeProjectPage extends WizardPage {

	IProject targetProject;
	String kernelTarget;
	
	Composite environmentsComposite;
	
	public UpgradeProjectPage(String pageName) {
		super(pageName);
	}

	public UpgradeProjectPage(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		
		Group projectSelectionGroup = new Group(composite, SWT.DEFAULT);
		projectSelectionGroup.setText("Project Selection");
		projectSelectionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		projectSelectionGroup.setLayout(new GridLayout(2, false));
		Label projectSelectionLabel = new Label(projectSelectionGroup, SWT.NONE);
		projectSelectionLabel.setText("Project to upgrade:");
		projectSelectionLabel.setFont(parent.getFont());
		projectSelectionLabel.setLayoutData(new GridData(SWT.DEFAULT, SWT.CENTER, false, false));
		
		ComboViewer projectList = new ComboViewer(projectSelectionGroup, SWT.DEFAULT);
		projectList.getCombo().setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		projectList.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((IProject)element).getName();
			}
		});
		projectList.addSelectionChangedListener((e) -> {
			targetProject = (IProject)((StructuredSelection)e.getSelection()).getFirstElement();
		});
		projectList.setContentProvider(new ArrayContentProvider());
		projectList.setInput(ResourcesPlugin.getWorkspace().getRoot().getProjects());
		IProject defaultSelection = ResourcesPlugin.getWorkspace().getRoot().getProject();
		if(defaultSelection == null && ResourcesPlugin.getWorkspace().getRoot().getProjects().length > 0) {
			defaultSelection = ResourcesPlugin.getWorkspace().getRoot().getProjects()[0];
		}
		projectList.setSelection(new StructuredSelection(defaultSelection));
		
		Group prosUpgradeGroup = new Group(composite, SWT.DEFAULT);
		prosUpgradeGroup.setText("PROS Configuration");
		prosUpgradeGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		prosUpgradeGroup.setLayout(new GridLayout(2, false));
		
		Label kernelComboLabel = new Label(prosUpgradeGroup, SWT.NONE);
		kernelComboLabel.setText("Kernel:");
		kernelComboLabel.setFont(parent.getFont());
		kernelComboLabel.setLayoutData(new GridData(SWT.DEFAULT, SWT.CENTER, false, false));

		ComboViewer kernelComboViewer = new ComboViewer(prosUpgradeGroup, SWT.DEFAULT);
		kernelComboViewer.getCombo().setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		kernelComboViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return (String) element;
			}
		});
		kernelComboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				kernelTarget = (String) ((StructuredSelection) event.getSelection()).getFirstElement();
				doFillEnvironments(environmentsComposite);
			}
		});
		kernelComboViewer.setContentProvider(new ArrayContentProvider());
		try {
			PROSActions actions = Activator.getPROSActions();
			kernelComboViewer.setInput(actions.resolveKernelLocalRequest("all"));
			kernelComboViewer.setSelection(new StructuredSelection(
					actions.resolveKernelLocalRequest("latest").parallelStream().collect(Collectors.toList()).get(0)));
		} catch (IOException e) {
			setErrorMessage(e.getMessage());
		}
		
		Label environmentLabel = new Label(prosUpgradeGroup, SWT.NONE);
		environmentLabel.setText("Retarget environments:");
		environmentLabel.setFont(parent.getFont());
		environmentLabel.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		
		environmentsComposite = new Composite(prosUpgradeGroup, SWT.NONE);
		environmentsComposite.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		RowLayout environmentsLayout = new RowLayout(SWT.HORIZONTAL);
		environmentsLayout.fill = true; environmentsLayout.justify = true;
		environmentsComposite.setLayout(environmentsLayout);
		doFillEnvironments(environmentsComposite);
		
		Label environmentHelp = new Label(prosUpgradeGroup, SWT.NONE);
		GridData environmentHelpLayoutData = new GridData(SWT.FILL, SWT.DEFAULT, false, false);
		environmentHelpLayoutData.horizontalSpan = 2;
		environmentHelp.setLayoutData(environmentHelpLayoutData);
		environmentHelp.setText("Checking none of the above will upgrade only existing environments that were detected.");
		environmentHelp.setFont(parent.getFont());
		
		Dialog.applyDialogFont(composite);
		setControl(composite);
	}
	
	@Override
	public boolean isPageComplete() {
		return validatePage();
	}

	private boolean validatePage() {
		if (!targetProject.exists()) {
			setErrorMessage("Project does not exist");
			return false;
		}
		
		try {
			PROSActions actions = Activator.getPROSActions();
			if (!actions.resolveKernelLocalRequest(kernelTarget).contains(kernelTarget)) {
				setErrorMessage("Invalid kernel selected");
				setMessage(null);
				return false;
			}
		} catch (IOException e) {
			setErrorMessage(e.getMessage());
			setMessage(null);
			return false;
		}
		setErrorMessage(null);
		setMessage(null);
		return true;
	}
	
	private final void doFillEnvironments(Composite parent) {
		if (parent != null) {
			for (Control control : parent.getChildren()) {
				control.dispose();
			}
			try {
				PROSActions actions = Activator.getPROSActions();
				Set<String> environments = actions.listEnvironments(kernelTarget).get(kernelTarget);
				String[] environmentsArray = environments.toArray(new String[] {});
				Arrays.sort(environmentsArray);
				for (String environment : environmentsArray) {
					Button button = new Button(parent, SWT.CHECK);
					button.setText(environment);
					button.setLayoutData(new RowData());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			parent.layout(true);
		}
	}

	public String getKernelTarget() {
		return kernelTarget;
	}
	
	public IProject getProject() {
		return targetProject;
	}
	
	public List<String> getEnvironments() {
		List<String> environments = new ArrayList<String>();
		for(Control control : environmentsComposite.getChildren()) {
			if(control instanceof Button && ((Button)control).getSelection()) {
				environments.add(((Button)control).getText());
			}
		}
		return environments;
	}
}
