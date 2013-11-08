package com.purduesigbots.newcortexproject.menus;

import org.eclipse.jface.action.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.*;
import org.eclipse.ui.menus.*;
import com.purduesigbots.newcortexproject.CmdLineUpdate;

/**
 * Maintains the dynamic "Switch project to PROS ***" menu item
 */
public class UpdateMenu extends CompoundContributionItem {
	private final CommandContributionItemParameter params;

	public UpdateMenu() {
		// Generate button up front
		final ImageDescriptor updateImg = ImageDescriptor.createFromURL(
			getClass().getResource("/icons/update.png"));
		params = new CommandContributionItemParameter(
			PlatformUI.getWorkbench().getActiveWorkbenchWindow(),
			"com.purduesigbots.newcortexproject.menus.updateCommand",
			"com.purduesigbots.newcortexproject.commands.updateCommand", SWT.NONE);
		// Populate with icon, PROS version, and more
		final String prosVersion = CmdLineUpdate.getPROSVersion();
		if (prosVersion != null && prosVersion.length() > 0)
			params.label = "Switch project to PROS " + prosVersion;
		else
			params.label = "Update project PROS version";
		params.mnemonic = "w";
		params.icon = updateImg;
		params.disabledIcon = updateImg;
		params.hoverIcon = updateImg;
		params.tooltip = "Switch current project to this revision of PROS";
		params.visibleEnabled = true;
	}
	protected IContributionItem[] getContributionItems() {
		return new IContributionItem[] { new CommandContributionItem(params) };
	}
}