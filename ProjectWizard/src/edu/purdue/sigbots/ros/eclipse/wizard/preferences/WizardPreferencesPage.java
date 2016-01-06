package edu.purdue.sigbots.ros.eclipse.wizard.preferences;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.ObjectInputStream.GetField;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.preference.*;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.IWorkbench;

import edu.purdue.sigbots.ros.cli.updater.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

/**
 * This class represents a preference page that
 * is contributed to the Preferences dialog. By 
 * subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows
 * us to create a page that is small and knows how to 
 * save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They
 * are stored in the preference store that belongs to
 * the main plug-in class. That way, preferences can
 * be accessed directly via the preference store.
 */

public class WizardPreferencesPage
	extends FieldEditorPreferencePage
	implements IWorkbenchPreferencePage {
	
	KernelListEditor kernelListEditor;

	public WizardPreferencesPage() {
		super(GRID);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Configure settings for interacting with the PROS CLI.");
	}
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	public void createFieldEditors() {
		addField(new DirectoryFieldEditor(PreferenceConstants.P_LOCAL_REPOSITORY, 
				"Local &Repository:", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceConstants.P_UPDATE_SITE, 
				"Update &Site:", getFieldEditorParent()));
		kernelListEditor = new KernelListEditor("Kernels", getFieldEditorParent());
		addField(kernelListEditor);
		addField(new EnvironmentPreferencesFieldEditor(PreferenceConstants.P_ENVIRONMENTS, "Default &Environments", getFieldEditorParent()));

		getControl().setSize(getControl().getSize());
	}
	
	@Override
	protected void performApply() {
		super.performApply();
		PROSActions actions;
		try {
			actions = Activator.getPROSActions();
			IPreferenceStore store = Activator.getDefault().getPreferenceStore();
			try {
				actions.setLocalKernelRepository(Paths.get(store.getString(PreferenceConstants.P_LOCAL_REPOSITORY)));
			} catch (IOException e) {
				showErrorDialog("Error saving settings", "There was an error saving the settings. Try again later.", IStatus.ERROR, e);
			}
			try {
				actions.setUpdateSite(new URL(store.getString(PreferenceConstants.P_UPDATE_SITE)));
			} catch (MalformedURLException e) {
				showErrorDialog("Bad URL input", "The provided URL is invalid.", IStatus.ERROR, e);
			} catch (IOException e) {
				showErrorDialog("Error saving settings", "There was an error saving the settings. Try again later.", IStatus.ERROR, e);
			}
			kernelListEditor.doLoad();
		} catch(IOException e) {
			showErrorDialog("PROS Error", "There was an error creating the PROS Updater interface.", IStatus.ERROR, e);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
		try {
			PROSActions actions = Activator.getPROSActions();
			IPreferenceStore store = Activator.getDefault().getPreferenceStore();
			store.setValue(PreferenceConstants.P_LOCAL_REPOSITORY, actions.getLocalRepositoryPath().toString());
			store.setValue(PreferenceConstants.P_UPDATE_SITE, actions.getUpdateSite().toExternalForm());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void showErrorDialog(String title, String message, int status, Throwable t) {
		StringWriter stringWriter = new StringWriter();
		PrintWriter traceWriter = new PrintWriter(stringWriter);
		t.printStackTrace(traceWriter);
		final String trace = stringWriter.toString();

		List<Status> childStatuses = new ArrayList<>();
		for (String line : trace.split(System.getProperty("line.separator"))) {
			childStatuses.add(new Status(status, Activator.PLUGIN_ID, line));
		}

		MultiStatus multiStatus = new MultiStatus(Activator.PLUGIN_ID, status,
				childStatuses.stream().toArray(s -> new Status[s]), t.getLocalizedMessage(), t);

		ErrorDialog.openError(this.getShell(), title, message, multiStatus);
	}
}