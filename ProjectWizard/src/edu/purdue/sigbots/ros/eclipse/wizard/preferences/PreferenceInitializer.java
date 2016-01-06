package edu.purdue.sigbots.ros.eclipse.wizard.preferences;

import java.io.IOException;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import edu.purdue.sigbots.ros.cli.updater.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		PROSActions actions = new PROSActions();
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		
		store.setDefault(PreferenceConstants.P_LOCAL_REPOSITORY, 
				actions.suggestLocalKernelRepository().toString());
		if(store.isDefault(PreferenceConstants.P_LOCAL_REPOSITORY)) {
			try {
				actions.setLocalKernelRepository(actions.suggestLocalKernelRepository());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		store.setDefault(PreferenceConstants.P_UPDATE_SITE,
				actions.suggestUpdateSite().toExternalForm());
		if(store.isDefault(PreferenceConstants.P_UPDATE_SITE)) {
			try {
				actions.setUpdateSite(actions.suggestUpdateSite());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		store.setDefault(PreferenceConstants.P_ENVIRONMENTS, "eclipse");
	}

}
