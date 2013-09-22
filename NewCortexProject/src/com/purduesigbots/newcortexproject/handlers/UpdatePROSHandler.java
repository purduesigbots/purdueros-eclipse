package com.purduesigbots.newcortexproject.handlers;

import java.io.*;
import org.eclipse.core.commands.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;

import com.purduesigbots.newcortexproject.menus.UpdateMenu;
import com.purduesigbots.newcortexproject.wizards.NewCortexProject;

/**
 * Prompts the user for confirmation, then switches the current project's PROS to the version
 * shipped with the UI.
 */
public class UpdatePROSHandler extends AbstractHandler {
	private static final String[] UPGRADE_FILES = new String[] {
		"firmware/libccos.a",
		"firmware/uniflash.jar",
		"include/API.h",
		"src/Makefile",
		"Makefile"
	};

	/**
	 * Fetches the current project by finding the owner of the uppermost editor.
	 * 
	 * This violates once-and-only-once with VEXFlashNG, but the hope is to make this plugin
	 * not depend on its brethren in the future, so this would break if cross-linked.
	 * 
	 * @param window the window to look for active projects
	 * @return the current project, or null if indeterminate
	 */
	public static IProject getCurrentProject(final IWorkbenchWindow window) {
		// Try scanning through all pages for something
		final IWorkbenchPage[] pages = window.getPages();
		for (IWorkbenchPage page : pages) {
			final IEditorPart editor = page.getActiveEditor();
			// Try the current editor
			if (editor != null) {
				final IFile file = (IFile)editor.getEditorInput().getAdapter(IFile.class);
				if (file != null && file.getProject().isOpen())
					return file.getProject();
			}
		}
		// Now try scanning the project list for a single open project
		final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		IProject onlyOpen = null;
		for (IProject project : projects) {
			if (project.isAccessible() && !project.isHidden() && project.isOpen()) {
				if (onlyOpen == null)
					onlyOpen = project;
				else
					// Multiple projects open, can't determine for sure what they want
					return null;
			}
		}
		return onlyOpen;
	}
	/**
	 * Updates PROS with prompts.
	 * 
	 * @param window the current workbench window
	 * @param mon progress monitor for status updates
	 */
	private static void updatePROS(final IWorkbenchWindow window, final IProgressMonitor mon) {
		final String text;
		final String prosVersion = UpdateMenu.getPROSVersion();
		// Check for the current project
		final IProject proj = getCurrentProject(window);
		if (proj == null)
			MessageDialog.openError(window.getShell(), "Update Error",
				"Open a project in the Project Explorer view to update PROS.");
		else {
			// Check for current PROS version and generate message
			if (prosVersion != null && prosVersion.length() > 0)
				text = "Update \"" + proj.getName() + "\" to PROS version " + prosVersion + "?";
			else
				text = "Update \"" + proj.getName() + "\" to this IDE's version of PROS?";
			// Got project ref, check for firmware/ folder
			final IFolder fwFolder = proj.getFolder(new Path("firmware"));
			if (fwFolder.exists()) {
				if (MessageDialog.openConfirm(window.getShell(), "Confirm Update",
						text))
					try {
						updatePROSBinary(proj, mon);
					} catch (IOException e) {
						// Backup strategy notification
						MessageDialog.openError(window.getShell(), "Update Error",
							"Could not update project binary!\nIf all else fails, create a " +
							"blank PROS project and copy \"firmware/libccos.a\" to the " +
							"broken project's \"firmware\" folder.");
					} catch (OperationCanceledException ignore) { }
				// Do not merge the if statements or extra error will pop up if user
				// cancels update
			} else
				MessageDialog.openError(window.getShell(), "Update Error",
					"\"" + proj.getName() + "\" does not appear to be a PROS project.");
		}
	}
	/**
	 * Actually upgrades the binary image in the given project.
	 * 
	 * @param proj the project to update
	 * @param mon the progress bar for status
	 * @throws IOException if an I/O error occurs
	 */
	private static void updatePROSBinary(final IProject proj, final IProgressMonitor mon)
			throws IOException {
		try {
			mon.beginTask("Updating PROS", 100 * UPGRADE_FILES.length);
			for (final String path : UPGRADE_FILES) {
				final IFile bin = proj.getFile(new Path(path));
				// Create stream to jar version
				final InputStream stream = NewCortexProject.openStream(path);
				// Update contents
				if (bin.exists())
					bin.setContents(stream, true, true, mon);
				else
					bin.create(stream, true, mon);
				// Clean up
				stream.close();
			}
		} catch (OperationCanceledException ignore) {
		} catch (CoreException e) {
			// Pass it up the tree for outer to handle
			if (e.getStatus() != null && e.getStatus().getCode() != IStatus.CANCEL)
				throw new IOException(e);
		} finally {
			mon.done();
		}
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		// Run when the button is clicked
		final UIJob confUpdate = new UIJob("Confirm Update") {
			public IStatus runInUIThread(final IProgressMonitor mon) {
				updatePROS(window, mon);
				// Always OK, even if no changes made
				return Status.OK_STATUS;
			}
		};
		confUpdate.schedule();
		return null;
	}
}