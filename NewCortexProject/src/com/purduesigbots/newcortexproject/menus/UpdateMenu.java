package com.purduesigbots.newcortexproject.menus;

import java.io.*;
import java.util.regex.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.*;
import org.eclipse.ui.menus.*;

import com.purduesigbots.newcortexproject.wizards.NewCortexProject;

/**
 * Maintains the dynamic "Switch project to PROS ***" menu item
 */
public class UpdateMenu extends CompoundContributionItem {
	private static final Pattern PROS_VERSION =
		Pattern.compile("PROS ([0-9]{1,2}[abro][0-9]{2})");
	private static String prosVersion = null;

	private static void updatePROSVersion() {
		// Read in the PROS binary and look for "PROS XXXX"
		final InputStream is = NewCortexProject.openStream("firmware/libccos.a");
		// Yes, this might garble binary characters. No, it doesn't matter in this case.
		final StringBuilder out = new StringBuilder(128 * 1024);
		try {
			// Load the PROS binary
			final BufferedReader br = new BufferedReader(new InputStreamReader(is));
			char[] buffer = new char[1024]; int n;
			while ((n = br.read(buffer)) > 0)
				out.append(buffer, 0, n);
			br.close();
			// Search string for "PROS ..."
			final Matcher m = PROS_VERSION.matcher(out);
			if (m.find())
				// Extract the version
				prosVersion = m.group(1);
			else
				prosVersion = "";
		} catch (Exception e) {
			// Cannot figure out our version, fail over to generic menu name
			prosVersion = "";
			// Try to swallow every exception possible since any exception might cause
			// ExceptionInInitializerError rendering eclipse unusable
		}
	}
	/**
	 * Gets the version of PROS shipped with this IDE by reading the PROS binary.
	 * 
	 * @return the PROS version, or "" if not able to determine
	 */
	public static synchronized String getPROSVersion() {
		if (prosVersion == null)
			updatePROSVersion();
		return prosVersion;
	}

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
		final String prosVersion = getPROSVersion();
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