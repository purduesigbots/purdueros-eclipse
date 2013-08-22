package com.purduesigbots.perspective.perspectives;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;

/**
 *  This class is meant to serve as an example for how various contributions 
 *  are made to a perspective. Note that some of the extension point id's are
 *  referred to as API constants while others are hardcoded and may be subject 
 *  to change. 
 */
public class MidnightCPerspective implements IPerspectiveFactory {

	private IPageLayout factory;

	public MidnightCPerspective() {
		super();
	}

	public void createInitialLayout(IPageLayout factory) {
		this.factory = factory;
		addViews();
		addActionSets();
		addNewWizardShortcuts();
		addPerspectiveShortcuts();
		addViewShortcuts();
	}

	private void addViews() {
		// Creates the overall folder layout. 
		// Note that each new Folder uses a percentage of the remaining EditorArea.
		IFolderLayout bottom = factory.createFolder("Console", IPageLayout.BOTTOM, 0.7f,
			factory.getEditorArea());
		bottom.addView(IConsoleConstants.ID_CONSOLE_VIEW);
		bottom.addView(IPageLayout.ID_PROBLEM_VIEW);
		bottom.addView(IPageLayout.ID_TASK_LIST);
		IFolderLayout left = factory.createFolder("Project Explorer", IPageLayout.LEFT, 0.2f,
			factory.getEditorArea());
		left.addView(IPageLayout.ID_PROJECT_EXPLORER);
		IFolderLayout right = factory.createFolder("Outline", IPageLayout.RIGHT, 0.75f,
			factory.getEditorArea());
		right.addView(IPageLayout.ID_OUTLINE);
	}

	private void addActionSets() {
		factory.addActionSet("org.eclipse.cdt.ui.OpenActionSet"); //NON-NLS-1
		factory.addActionSet("org.eclipse.cdt.ui.CodingActionSet"); //NON-NLS-1
	}

	private void addPerspectiveShortcuts() {
		factory.addPerspectiveShortcut("org.eclipse.cdt.ui.CPerspective"); //NON-NLS-1
	}

	private void addNewWizardShortcuts() {
		factory.addNewWizardShortcut("com.purduesigbots.newcortexproject.wizards.NewCortexProject");//NON-NLS-1
		factory.addNewWizardShortcut("org.eclipse.cdt.ui.wizards.NewFileCreationWizard");//NON-NLS-1
		factory.addNewWizardShortcut("org.eclipse.cdt.ui.wizards.NewFolderCreationWizard");//NON-NLS-1
		factory.addNewWizardShortcut("org.eclipse.cdt.ui.wizards.NewSourceFileCreationWizard");//NON-NLS-1
		factory.addNewWizardShortcut("org.eclipse.cdt.ui.wizards.NewHeaderFileCreationWizard");//NON-NLS-1
	}

	private void addViewShortcuts() {
		factory.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);
		factory.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
		factory.addShowViewShortcut(IPageLayout.ID_OUTLINE);
		factory.addShowViewShortcut(IPageLayout.ID_PROJECT_EXPLORER);
		factory.addShowViewShortcut(IPageLayout.ID_TASK_LIST);
	}

}
