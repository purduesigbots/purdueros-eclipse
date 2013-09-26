package com.purduesigbots.vexflash.handlers;

// XXX Reflection is ugly but it ensures no NoClassDefFoundError if terminals is uninstalled
import java.lang.reflect.*;
import java.io.File;
import java.util.*;

import org.eclipse.core.commands.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.UIJob;

import com.purduesigbots.vexflash.*;

/**
 * Handles requests to upload the current project to the VEX Cortex.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class VexUploadHandler extends AbstractHandler {
	/**
	 * Checks a view to see if it is a terminal and can be closed.
	 * 
	 * @param view the view to inspect
	 * @param portName the port name to match against
	 * @return whether the view was a terminal and it was closed successfully
	 */
	private static boolean checkCloseTerminal(final IViewPart view, final String portName) {
		if (view.getClass().getSimpleName().equals("TerminalView")) {
			// Terminal open, maybe disconnect it!
			try {
				// getActiveConnection().getFullSummary()
				final String summary = view.getTitleToolTip();
				// If the summary contains our port name
				if (summary != null && summary.toString().toLowerCase().contains(portName)) {
					final Method m = view.getClass().getMethod("onTerminalDisconnect",
						(Class<?>[])null);
					m.invoke(view, (Object[])null);
					return true;
				}
				// If we die here, not an issue
			} catch (Exception ignore) { ignore.printStackTrace(); }
		}
		return false;
	}
	/**
	 * Reopens the specified terminals.
	 * 
	 * @param reopen the terminals to re-open
	 */
	private static void reopenTerminals(final List<IViewPart> reopen) {
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
	 * The saved serial port.
	 */
	private PortPrompter port;
	/**
	 * Flag to stop multiple "Upload"s from running
	 */
	private volatile boolean uploading;
	/**
	 * Flash utility object for the current upload.
	 */
	private FlashUtility util;
	/**
	 * Eclipse window used for uploading.
	 */
	private IWorkbenchWindow window;

	/**
	 * Default constructor to create port prompter object.
	 */
	public VexUploadHandler() {
		port = new PortPrompter();
	}
	/**
	 * Saves everything that is open with prompt.
	 */
	private void bulkSave() {
		final IWorkbenchPage[] pages = window.getPages();
		for (IWorkbenchPage page : pages)
			page.saveAllEditors(true);
	}
	/**
	 * Kills any terminals using the upload port.
	 * 
	 * @param portName the port name to match terminals against
	 */
	private List<IViewPart> closeTerminals(final String portName) {
		final IWorkbenchPage[] pages = window.getPages();
		final List<IViewPart> closed = new LinkedList<IViewPart>();
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
	@Override
	public void dispose() {
		super.dispose();
		port = null;
		util = null;
	}
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		if (window != null && lockUpload()) {
			final IProject project = getCurrentProject();
			bulkSave();
			if (project == null)
				// Can't tell for sure
				uploadError("Open a project in the Project Explorer view to select a " +
					"program to upload.");
			else
				setSerialPort(project);
		}
		return null;
	}
	/**
	 * Fetches the current project by finding the owner of the uppermost editor.
	 * 
	 * @return the current project, or null if indeterminate
	 */
	protected IProject getCurrentProject() {
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
	private synchronized boolean lockUpload() {
		final boolean u = uploading;
		if (!u) uploading = true;
		return !u;
	}
	private void notPluggedInError() {
		uploadError("VEX Programming Kit or USB A-to-A cable not found.\n" +
			"Check that VEX Programming Kit or USB Tether is tightly plugged into computer.");
	}
	/**
	 * Actually uploads the code to the processor.
	 *
	 * @param project the binary file to upload
	 * @param mon the progress monitor to indicate progress
	 * @param project the project to upload
	 * @throws Exception if an I/O error occurs
	 */
	protected void processorUpload(final File file, final IProgressMonitor mon,
			final IProject project) throws SerialException {
		// Create flasher
		final String ser = port.getPort();
		final Indicator output = new ProgressMonitorIndicator(mon);
		if (ser == null)
			// Was plugged in, is no more
			notPluggedInError();
		else {
			util.setup(file, new String[0], ser);
			// Begin upload -- initialize system
			try {
				mon.beginTask("Uploading " + project.getName() + " to VEX Cortex", 100);
				util.program(output);
			} finally {
				util.end();
			}
			// All done
			mon.done();
		}
	}
	/**
	 * Processes the upload after terminals are closed.
	 * 
	 * @param project the project to upload
	 * @param cl the terminals closed for uploading
	 * @param bin the file to upload
	 */
	private void procUpload(final IProject project, final List<IViewPart> cl, final File bin) {
		final Job job = new Job("Uploading " + project.getName()) {
			protected IStatus run(final IProgressMonitor mon) {
				try {
					// Wait for terminals to close fully
					if (!cl.isEmpty())
						Thread.sleep(1000L);
					// Upload to processor
					processorUpload(bin, mon, project);
					util = null;
				} catch (OperationCanceledException e) {
					unlockUpload();
					// Die quietly if cancelled
					return Status.CANCEL_STATUS;
				} catch (Exception uploadError) {
					final String msg = uploadError.getMessage();
					if (msg == null)
						uploadError("Failed to upload the project to VEX Cortex");
					else
						uploadError(msg);
				}
				// Reopen the terminals in a UI job
				final UIJob ui = new UIJob("Reopen closed terminals") {
					public IStatus runInUIThread(IProgressMonitor monitor) {
						reopenTerminals(cl);
						unlockUpload();
						return Status.OK_STATUS;
					}
				};
				ui.schedule();
				return Status.OK_STATUS;
			}
		};
		job.setPriority(Job.LONG);
		job.schedule();
	}
	/**
	 * Causes the serial port to be re-selected if necessary.
	 * 
	 * @param project the current project
	 */
	public void setSerialPort(final IProject project) {
		util = new VexFlash();
		if (port.hasSavedPort())
			// Ready!
			startUpload(project);
		else {
			// Get a list, convert to array
			final List<PortFinder.Serial> in = util.locateSerial();
			final PortFinder.Serial[] comms = in.toArray(
				new PortFinder.Serial[in.size()]);
			if (comms.length > 0) {
				// Port list provider
				final IStructuredContentProvider ports = new IStructuredContentProvider() {
					public void inputChanged(final Viewer viewer, final Object old,
							final Object nw) {
					}
					public void dispose() { }
					public Object[] getElements(final Object input) {
						// Return list
						return comms;
					}
				};
				final ILabelProvider portLabels = new ILabelProvider() {
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
						return ((PortFinder.Serial)obj).toString();
					}
				};
				final UIJob getPort = new UIJob("Select Upload Port") {
					public IStatus runInUIThread(final IProgressMonitor mon) {
						final ListDialog portSelect = new ListDialog(window.getShell());
						// Show port picker dialog
						portSelect.setTitle("Select Port");
						portSelect.setMessage("Select VEX communications port:");
						portSelect.setContentProvider(ports);
						portSelect.setLabelProvider(portLabels);
						portSelect.setInitialSelections(new PortFinder.Serial[] { comms[0] });
						portSelect.setInput(comms);
						// Get result from user
						if (portSelect.open() == ListDialog.OK) {
							final Object[] res = portSelect.getResult();
							if (res.length == 1) {
								port.savePort(((PortFinder.Serial)res[0]).getComIdentifier());
								startUpload(project);
								return Status.OK_STATUS;
							}
						}
						return Status.CANCEL_STATUS;
					}
				};
				if (comms.length == 1) {
					// Obvious?
					port.savePort(comms[0].getComIdentifier());
					startUpload(project);
				} else
					getPort.schedule();
			} else
				// Not plugged in
				notPluggedInError();
		}
	}
	/**
	 * Starts the processor upload.
	 * 
	 * @param project the current project
	 */
	protected void startUpload(final IProject project) {
		final Job uploadJob = new Job("Compiling " + project.getName()) {
			protected IStatus run(final IProgressMonitor mon) {
				// Check for binary file
				final File prj = project.getLocation().toFile();
				final File bin = new File(new File(prj, "bin"), "output.bin");
				try {
					// Compile project
					project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, mon);
					// Check for errors and warnings!!!
					final IMarker[] markers = project.findMarkers(null, true,
							IResource.DEPTH_INFINITE);
					for (IMarker marker : markers)
						if (marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) ==
								IMarker.SEVERITY_ERROR)
							throw new RuntimeException("Compilation error found!");
					// DRY
					if (!bin.exists())
						throw new RuntimeException("No output file found!");
				} catch (OperationCanceledException e) {
					unlockUpload();
					// Die quietly if cancelled
					return Status.CANCEL_STATUS;
				} catch (Exception e) {
					uploadError("Errors occurred when compiling program!\nA full list of " +
						"errors and warnings is available in the Problems tab.");
					return Status.OK_STATUS;
				}
				// Save state
				final UIJob ui = new UIJob("Close terminals") {
					public IStatus runInUIThread(IProgressMonitor monitor) {
						final List<IViewPart> cl = closeTerminals(port.getPort());
						procUpload(project, cl, bin);
						return Status.OK_STATUS;
					}
				};
				ui.setPriority(Job.SHORT);
				ui.schedule();
				// Restore state
				return Status.OK_STATUS;
			}
		};
		// Run upload task
		uploadJob.setPriority(Job.LONG);
		uploadJob.schedule();
	}
	private synchronized void unlockUpload() {
		uploading = false;
	}
	/**
	 * Raises an "upload error" message with the given text.
	 * 
	 * @param message the error message to display
	 */
	protected void uploadError(final String message) {
		final UIJob job = new UIJob("Upload Error") {
			public IStatus runInUIThread(final IProgressMonitor mon) {
				MessageDialog.openError(window.getShell(), "Upload Error", message);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
		unlockUpload();
	}
}
