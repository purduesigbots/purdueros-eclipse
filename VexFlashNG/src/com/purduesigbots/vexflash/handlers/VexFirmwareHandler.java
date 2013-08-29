package com.purduesigbots.vexflash.handlers;

import java.io.File;
import java.net.URL;

import org.eclipse.core.commands.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;

/**
 * Handles requests to update the Mastercode on the VEX Cortex.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class VexFirmwareHandler extends AbstractHandler {
	private static void error(final IWorkbenchWindow window, final String message) {
		if (window != null) {
			final UIJob job = new UIJob("Firmware Upgrade Error") {
				public IStatus runInUIThread(final IProgressMonitor mon) {
					MessageDialog.openError(window.getShell(), "Firmware Upgrade Error",
						message);
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		}
	}

	/**
	 * Run when the command is executed.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final String prop = System.getProperty("eclipse.home.location");
		final String os = System.getProperty("os.name");
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		if (os != null && os.toLowerCase().startsWith("win")) {
			if (prop != null && prop.length() > 0) try {
				// Fetch the Eclipse homedir
				final URL url = new java.net.URL(prop);
				final File eclipseDir = new File(url.getFile()).getCanonicalFile();
				if (eclipseDir != null) {
					// Extract the homedir, go ../firmware_upgrade
					final File uploadDir = new File(eclipseDir, "firmware_upgrade");
					final File uploadExe = new File(uploadDir, "VEXnetUpgrade.exe");
					final ProcessBuilder builder = new ProcessBuilder();
					builder.command(uploadExe.getAbsolutePath());
					builder.directory(uploadDir);
					builder.start();
				}
			} catch (Exception e) {
				error(window, "VEXnet Firmware Upgrade is missing or incorrectly configured.\n" +
					"The most recent version of this utility is available on the VEX Wiki.");
			}
		} else {
			error(window, "VEXnet Firmware Upgrade only works on Windows computers.");
		}
		return null;
	}
}
