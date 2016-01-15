package edu.purdue.sigbots.ros.eclipse.wizard.newproject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceNode;
import org.eclipse.jface.preference.IPreferencePage;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.dialogs.ProjectContentsLocationArea;

import edu.purdue.sigbots.ros.cli.updater.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;
import edu.purdue.sigbots.ros.eclipse.wizard.preferences.PreferenceConstants;
import edu.purdue.sigbots.ros.eclipse.wizard.preferences.WizardPreferencesPage;

// To the future: a lot of this code was yanked from the WizardNewProjectCreationPage source
@SuppressWarnings("restriction")
public class PROSWizardPage extends WizardPage implements IWizardPage {

	private Text projectNameField;
	private ProjectContentsLocationArea locationArea;
	private String kernelTarget;
	private Composite environmentsComposite;

	public PROSWizardPage(String pageName) {
		super(pageName);
	}

	public PROSWizardPage(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);

		GridLayout layout = new GridLayout(1, false);
		composite.setLayout(layout);

		// Label label = new Label(composite, SWT.NONE);
		// label.setText("Hello World!");

		Group projectDescriptionGroup = new Group(composite, SWT.DEFAULT);
		projectDescriptionGroup.setText("Project Configuration");
		projectDescriptionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		projectDescriptionGroup.setLayout(new GridLayout(1, false));

		createProjectNameGroup(projectDescriptionGroup);
		locationArea = new ProjectContentsLocationArea((message, info) -> {
			if (info) {
				setMessage(message, IStatus.INFO);
				setErrorMessage(null);
			} else {
				setErrorMessage(message);
			}
			boolean valid = message == null;
			if (valid) {
				valid = validatePage();
			}
			setPageComplete(valid);
		} , projectDescriptionGroup);

		setButtonLayoutData(locationArea.getBrowseButton());

		Group prosConfigurationGroup = new Group(composite, SWT.DEFAULT);
		prosConfigurationGroup.setText("PROS Configuration");
		prosConfigurationGroup.setLayout(new GridLayout(1, false));
		prosConfigurationGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		createPROSConfigurationGroup(prosConfigurationGroup);
		
		Button configPROSButton = new Button(prosConfigurationGroup, SWT.PUSH);
		configPROSButton.setText("Configure PROS");
		configPROSButton.addListener(SWT.Selection, (e) -> {
			IPreferencePage page = new WizardPreferencesPage();
			
			PreferenceManager manager = PlatformUI.getWorkbench().getPreferenceManager();
			PreferenceDialog dialog = new PreferenceDialog(getShell(), manager);
			dialog.setSelectedNode("edu.purdue.sigbots.ros.eclipse.wizard.preferences.WizardPreferencesPage");
			dialog.create();
			dialog.setMessage("Configure PROS");
			dialog.open();
		});
		configPROSButton.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));

		setPageComplete(validatePage());
		setErrorMessage(null);
		setMessage(null);
		Dialog.applyDialogFont(composite);
		setControl(composite);
	}

	@Override
	public boolean isPageComplete() {
		return validatePage();
	}

	private final void createPROSConfigurationGroup(Composite parent) {
		Composite group = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(4, false);
		group.setLayout(layout);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label kernelComboLabel = new Label(group, SWT.NONE);
		kernelComboLabel.setText("Kernel:");
		kernelComboLabel.setFont(parent.getFont());

		ComboViewer kernelComboViewer = new ComboViewer(group, SWT.DEFAULT);
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

		createEnvironmentSelector(group);
	}

	// Grabbed from WizardNewProjectCreationPage
	private final void createProjectNameGroup(Composite parent) {
		Composite projectGroup = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		projectGroup.setLayout(layout);
		projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Label projectLabel = new Label(projectGroup, SWT.NONE);
		projectLabel.setText("Project name:");
		projectLabel.setFont(parent.getFont());

		projectNameField = new Text(projectGroup, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = 250;
		projectNameField.setLayoutData(data);
		projectNameField.setFont(parent.getFont());
		projectNameField.setFocus();

		projectNameField.addListener(SWT.Modify, e -> {
			setLocationForSelection();
			setPageComplete(validatePage());
		});
		BidiUtils.applyBidiProcessing(projectNameField, BidiUtils.BTD_DEFAULT);
	}

	private boolean validatePage() {
		IWorkspace workspace = IDEWorkbenchPlugin.getPluginWorkspace();

		String projectFieldContents = getProjectNameFieldValue();
		if (projectFieldContents.isEmpty()) {
			setErrorMessage(null);
			setMessage(IDEWorkbenchMessages.WizardNewProjectCreationPage_projectNameEmpty);
			return false;
		}

		IStatus nameStatus = workspace.validateName(projectFieldContents, IResource.PROJECT);
		if (!nameStatus.isOK()) {
			setErrorMessage(nameStatus.getMessage());
			return false;
		}

		IProject handle = ResourcesPlugin.getWorkspace().getRoot().getProject(getProjectNameFieldValue());
		if (handle.exists()) {
			setErrorMessage(IDEWorkbenchMessages.WizardNewProjectCreationPage_projectExistsMessage);
			return false;
		}
		locationArea.setExistingProject(handle);
		String validLocationMessage = locationArea.checkValidLocation();
		if (validLocationMessage != null) {
			setErrorMessage(validLocationMessage);
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

	void setLocationForSelection() {
		locationArea.updateProjectName(getProjectNameFieldValue());
	}

	private String getProjectNameFieldValue() {
		if (projectNameField == null) {
			return null;
		}
		return projectNameField.getText().trim();
	}

	private final void createEnvironmentSelector(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		composite.setLayout(new GridLayout(4, false));

		Label label = new Label(composite, SWT.NONE);
		label.setText("Environments:");
		label.setFont(parent.getFont());
		label.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));

		environmentsComposite = new Composite(composite, SWT.NONE);
		environmentsComposite.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false));
		RowLayout enviromentsLayout = new RowLayout(SWT.HORIZONTAL);
		enviromentsLayout.fill = true;
		enviromentsLayout.justify = true;
		environmentsComposite.setLayout(enviromentsLayout);
		doFillEnvironments(environmentsComposite);
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
					if (environment.equalsIgnoreCase("eclipse")) {
						button.setSelection(true);
						button.setEnabled(false);
					} else if(Arrays.asList( // yay method chaining! Get the list of default environments and check if this environment is one of them
							Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.P_ENVIRONMENTS).split(PreferenceConstants.P_ENVIRONMENTS_SPLITTER))
							.contains(environment)) {
						button.setSelection(true);
					}
					button.setLayoutData(new RowData());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			parent.layout(true);
		}
	}
	
	public synchronized List<String> getEnvironmentSelection() {
		List<String> environments = new ArrayList<String>();
		for(Control control : environmentsComposite.getChildren()) {
			if(control instanceof Button && ((Button)control).getSelection()) {
				environments.add(((Button)control).getText());
			}
		}
		return environments;
	}
	
	public String getKernelTarget() {
		return kernelTarget;
	}
	
	public String getProjectName() {
		return projectNameField.getText();
	}
	
	public URI getProjectURI() {
		return locationArea.isDefault() ? null : locationArea.getProjectLocationURI();
	}
}
