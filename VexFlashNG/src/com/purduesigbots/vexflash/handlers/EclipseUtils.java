package com.purduesigbots.vexflash.handlers;

// Reflection is ugly but it ensures no NoClassDefFoundError if terminals is uninstalled
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.*;
import org.eclipse.ui.progress.UIJob;

/**
 * Contains utility functions for working with Eclipse and SWT.
 */
public final class EclipseUtils {
	// Name and extension filter for file dialog
	private static final String[] FD_EXT = new String[] { "*.*" };
	private static final String[] FD_NAME = new String[] { "All files (under 128 KB)" };

	/**
	 * Saves everything that is open with prompt.
	 * 
	 * @param window the current workbench window
	 */
	public static void bulkSave(final IWorkbenchWindow window) {
		final IWorkbenchPage[] pages = window.getPages();
		for (IWorkbenchPage page : pages)
			page.saveAllEditors(true);
	}
	/**
	 * Checks a view to see if it is a terminal and can be closed.
	 * 
	 * @param view the view to inspect
	 * @param portName the port name to match against (converted to all lower case)
	 * @return whether the view was a terminal and it was closed successfully
	 */
	private static boolean checkCloseTerminal(final IViewPart view, final String portName) {
		if (view.getClass().getSimpleName().equals("TerminalView")) {
			// Terminal open, maybe disconnect it!
			try {
				// getActiveConnection().getFullSummary()
				final String summary = view.getTitleToolTip();
				// If the summary contains our port name
				if (summary.toString().toLowerCase().contains(portName)) {
					final Method m = view.getClass().getMethod("onTerminalDisconnect",
						(Class<?>[])null);
					m.invoke(view, (Object[])null);
					return true;
				}
				// If we die here, not an issue
			} catch (Exception ignore) { }
		}
		return false;
	}
	/**
	 * Kills any terminals using the upload port.
	 * 
	 * @param window the current workbench window
	 * @param portName the port name to match terminals against
	 */
	public static Collection<IViewPart> closeTerminals(final IWorkbenchWindow window,
			final String portName) {
		final IWorkbenchPage[] pages = window.getPages();
		final Collection<IViewPart> closed = new LinkedList<IViewPart>();
		for (IWorkbenchPage page : pages) {
			final IViewReference[] refs = page.getViewReferences();
			// get around deprecation on getViews()
			for (IViewReference ref : refs) {
				final IViewPart vv = ref.getView(true);
				// Close?
				if (vv != null && checkCloseTerminal(vv, portName.toLowerCase()))
					closed.add(vv);
			}
		}
		return closed;
	}
	/**
	 * Compiles the specified Eclipse project.
	 * 
	 * @param project the project to compile
	 * @param mon the progress monitor to display build progress
	 * @throws CoreException if an error occurs when compiling or building the project
	 */
	public static void compileProject(final IProject project, final IProgressMonitor mon)
			throws CoreException {
		// Compile project
		project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, mon);
		// Check for errors and warnings
		final IMarker[] markers = project.findMarkers(null, true, IResource.DEPTH_INFINITE);
		for (IMarker marker : markers) {
			final int severity = marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
			if (severity == IMarker.SEVERITY_ERROR)
				// Found an error message
				throw new CoreException(new Status(IStatus.ERROR, EclipseUtils.class.
					getCanonicalName(), marker.getType()));
			}
	}
	/**
	 * Displays a pop-up on the UI thread with the given title and message.
	 * 
	 * @param window the current workbench window
	 * @param title the message title
	 * @param message the message text, where '\n' will insert a new line
	 */
	public static void displayError(final IWorkbenchWindow window, final String title,
			final String message) {
		if (window != null) {
			final UIJob job = new UIJob(title) {
				public IStatus runInUIThread(final IProgressMonitor mon) {
					MessageDialog.openError(window.getShell(), title, message);
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		}
	}
	/**
	 * Fetches the current project by finding the owner of the uppermost editor.
	 * 
	 * @param window the current workbench window
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
				if (file != null && file.getProject() != null && file.getProject().isOpen())
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
	 * Reopens the specified terminals.
	 * 
	 * @param reopen the terminals to re-open
	 */
	public static void reopenTerminals(final Collection<IViewPart> reopen) {
		for (IViewPart part : reopen) {
			// part should be an ITerminalView
			try {
				final Method m = part.getClass().getMethod("onTerminalConnect",
					(Class<?>[])null);
				m.invoke(part, (Object[])null);
				// If we die here, not an issue
			} catch (Exception ignore) { }
		}
	}
	/**
	 * Opens a popup dialog which allows the user to select an item from a list.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param window the active workbench window
	 * @param title the dialog title
	 * @param message the dialog message
	 * @param choices the available choices
	 * @return the selected object, or null if the dialog was cancelled
	 */
	public static Object selectDialog(final IWorkbenchWindow window, final String title,
			final String message, final Object[] choices) {
		final IStructuredContentProvider provider = new IStructuredContentProvider() {
			public void inputChanged(final Viewer viewer, final Object old,
					final Object nw) {
			}
			public void dispose() { }
			public Object[] getElements(final Object input) {
				// Return list
				return choices;
			}
		};
		final ILabelProvider labels = new ILabelProvider() {
			public void addListener(ILabelProviderListener listener) {
			}
			public void dispose() {
			}
			public boolean isLabelProperty(Object property, String propertyName) {
				return false;
			}
			public void removeListener(ILabelProviderListener listener) {
			}
			public Image getImage(Object obj) {
				return null;
			}
			public String getText(Object obj) {
				// Return string representation of object
				if (obj == null)
					return "";
				else
					return obj.toString();
			}
		};
		final ListDialog portSelect = new ListDialog(window.getShell());
		// Show picker dialog
		portSelect.setTitle(title);
		portSelect.setMessage(message);
		portSelect.setContentProvider(provider);
		portSelect.setLabelProvider(labels);
		portSelect.setInitialSelections(new Object[] { choices[0] });
		portSelect.setInput(choices);
		// Get result from user
		if (portSelect.open() == ListDialog.OK) {
			final Object[] res = portSelect.getResult();
			if (res.length == 1)
				return res[0];
		}
		return null;
	}
	/**
	 * Gets a file to download from the user.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param window the current workbench window
	 * @return the file selected, or null if it was not found
	 */
	public static File selectFile(final IWorkbenchWindow window) {
		final FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN);
		dialog.setOverwrite(false);
		dialog.setText("Select file to download");
		dialog.setFilterExtensions(FD_EXT);
		dialog.setFilterNames(FD_NAME);
		// Open the popup
		final String path = dialog.open();
		if (path == null)
			return null;
		// Resolve to file system
		File file = new File(path);
		try {
			file = file.getCanonicalFile();
		} catch (IOException e) {
			file = file.getAbsoluteFile();
		}
		// Throw out /dev/xxx and directories
		if (!file.canRead() || !file.isFile())
			return null;
		return file;
	}
	/**
	 * Gets a target upload folder from the user. Due to the order in which the FS is scanned
	 * a specific file can't be uploaded at this time, as that would require a connection to
	 * be made before the GUI is opened.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param window the current workbench window
	 * @return the folder selected, or null if it was not found
	 */
	public static File selectFolder(final IWorkbenchWindow window) {
		final DirectoryDialog dialog = new DirectoryDialog(window.getShell(), SWT.OPEN);
		dialog.setText("Select folder to place uploaded files [will overwrite existing files]");
		// Open the popup
		final String path = dialog.open();
		if (path == null)
			return null;
		// Resolve to file system
		File file = new File(path);
		try {
			file = file.getCanonicalFile();
		} catch (IOException e) {
			file = file.getAbsoluteFile();
		}
		// Throw out /dev/xxx and directories
		if (!file.canRead() || !file.isDirectory())
			return null;
		return file;
	}
}