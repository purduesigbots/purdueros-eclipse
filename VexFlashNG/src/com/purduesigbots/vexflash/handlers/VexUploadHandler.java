package com.purduesigbots.vexflash.handlers;

import java.io.*;
import java.util.*;

import org.eclipse.core.commands.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.*;
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
	 * The maximum allowed downloadable file size in bytes.
	 */
	public static final int MAX_FILE_SIZE = 128 * 1024;

	/**
	 * Ends the upload process.
	 * 
	 * @param cl the list of Terminal view objects that were closed before uploading
	 */
	private static void endUpload(final Collection<IViewPart> cl) {
		// Reopen the terminals in a UI job
		final UIJob ui = new UIJob("Reopen closed terminals") {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				EclipseUtils.reopenTerminals(cl);
				return Status.OK_STATUS;
			}
		};
		ui.setPriority(UIJob.SHORT);
		ui.schedule();
	}

	/**
	 * Lock for synchronizing processorUpload()
	 */
	private final Object lockProcessorUpload;
	/**
	 * Lock for synchronizing procUpload()
	 */
	private final Object lockProcUpload;
	/**
	 * The saved serial port.
	 */
	private final PortPrompter port;
	/**
	 * VEX flash utilities should now be fully reusable!
	 */
	private final VexFlash util;
	/**
	 * Eclipse window used for uploading.
	 */
	private IWorkbenchWindow window;

	/**
	 * Default constructor to create port prompter object.
	 */
	public VexUploadHandler() {
		lockProcessorUpload = new Object();
		lockProcUpload = new Object();
		port = new PortPrompter();
		util = new VexFlash();
	}
	/**
	 * Compiles the current project in Eclipse. If compilation fails, the upload will not start
	 * and an error message will be displayed. If compilation succeeds, the procUpload method
	 * will start.
	 * 
	 * @param project the current project
	 * @param mode the command mode in use
	 * @param target the port to use for uploading
	 */
	protected IStatus compile(final IProject project, final int mode, final String target,
			final IProgressMonitor mon) {
		// Check for binary file
		final File prj = project.getLocation().toFile();
		final File bin = new File(new File(prj, "bin"), "output." + util.getExtension());
		try {
			// Compile program
			EclipseUtils.compileProject(project, mon);
			if (!bin.canRead() || !bin.isFile())
				uploadError("No program binary was generated in the \"bin\" directory.\n" +
					"Try using \"Project > Clean\" and uploading again.");
			else
				// Construct the UploadParams object and call out
				startUpload(new UploadParams(mode, target, project.getName(), bin));
		} catch (OperationCanceledException e) {
			// Die quietly if cancelled
			return Status.CANCEL_STATUS;
		} catch (CoreException e) {
			uploadError("Errors occurred when compiling program!\nA full list of " +
				"errors and warnings is available in the Problems view.");
		}
		return Status.OK_STATUS;
	}
	/**
	 * Computes the action to be performed and raises the necessary dialogs.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param event the event which triggered the execution of the VexUploadHandler
	 * @param target the port to use for uploading
	 */
	protected void computeAction(final ExecutionEvent event, final String target) {
		final String _id = event.getCommand().getId();
		final String id = (_id == null) ? "upload" : _id;
		if (id.contains("uploadFile"))
			// File upload
			promptFileUpload(target);
		else if (id.contains("downloadFile"))
			// File download
			promptFileDownload(target);
		else {
			// Upload or upload clean
			final int mode = (id.contains("Preserve") ? UploadParams.MODE_FW :
				UploadParams.MODE_CLEAN);
			// Calculate project, then bulk save
			final IProject project = EclipseUtils.getCurrentProject(window);
			if (project != null) {
				EclipseUtils.bulkSave(window);
				final Job compileJob = new Job("Compiling " + project.getName()) {
					protected IStatus run(final IProgressMonitor mon) {
						return compile(project, mode, target, mon);
					}
				};
				// Run compile task
				compileJob.setPriority(Job.LONG);
				compileJob.schedule();
			} else
				// No project could be determined
				uploadError("Open a project in the Project Explorer view to select a " +
					"program to upload.");
		}
	}
	@Override
	public void dispose() {
		super.dispose();
	}
	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		if (window != null) {
			// In case this is not the UI thread, put the port selection on the UI thread
			final UIJob job = new UIJob("Select Port") {
				public IStatus runInUIThread(IProgressMonitor monitor) {
					return setSerialPort(event);
				}
			};
			job.schedule();
		}
		return null;
	}
	/**
	 * Actually uploads the code to the processor.
	 *
	 * @param params the upload parameters computed by computeAction()
	 * @param mon the progress monitor to use to report upload status
	 * @throws SerialException if an I/O error occurs
	 */
	private void processorUpload(final UploadParams params, final IProgressMonitor mon)
			throws SerialException {
		synchronized (lockProcessorUpload) {
			// Create flasher and populate the parameters
			final Indicator output = new ProgressMonitorIndicator(mon);
			util.setup(params);
			// Begin upload
			try {
				mon.beginTask(params.getDescription(), 100);
				util.program(output);
			} finally {
				util.end();
			}
			// All done
			mon.done();
		}
	}
	/**
	 * Processes the upload after terminals are closed. Can be called from any thread, as the
	 * compile task does not run on the UI thread.
	 * 
	 * @param params the upload parameters computed by computeAction()
	 */
	private void procUpload(final UploadParams params, final Collection<IViewPart> cl) {
		synchronized (lockProcUpload) {
			final Job job = new Job(params.getDescription()) {
				protected IStatus run(final IProgressMonitor mon) {
					IStatus status = Status.OK_STATUS;
					try {
						// Upload to processor
						processorUpload(params, mon);
					} catch (OperationCanceledException e) {
						// Die quietly if cancelled
						status = Status.CANCEL_STATUS;
					} catch (SerialException uploadError) {
						// Oh no!
						final String msg = uploadError.getMessage();
						if (msg == null)
							uploadError("Failed to upload the project to the VEX Cortex.");
						else
							uploadError(msg);
					}
					// Ensure that the terminals are re-opened if need be
					endUpload(cl);
					return status;
				}
			};
			job.setPriority(Job.LONG);
			// Wait for terminals to close fully if required
			if (cl.isEmpty())
				job.schedule();
			else
				job.schedule(1500L);
		}
	}
	/**
	 * Raises a dialog box asking the user to specify the file to download.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param target the port to use for uploading
	 */
	private void promptFileDownload(final String target) {
		final File file = EclipseUtils.selectFile(window);
		if (file != null) {
			final long size = file.length();
			if (size == 0 || size >= MAX_FILE_SIZE)
				// Not a good file to upload
				uploadError("This file is empty or too large (more than 128 KB) to download.");
			else
				startUpload(new UploadParams(UploadParams.MODE_DOWNLOAD_FS, target, null, file));
		}
	}
	/**
	 * Raises a dialog box asking the user to specify the folder to place uploaded files.
	 * 
	 * Must be run in the UI thread!
	 * 
	 * @param target the port to use for uploading
	 */
	private void promptFileUpload(final String target) {
		final File folder = EclipseUtils.selectFolder(window);
		if (folder != null)
			startUpload(new UploadParams(UploadParams.MODE_UPLOAD_FS, target, null, folder));
	}
	/**
	 * Opens a dialog for the user to re-select the serial port if necessary.
	 * 
	 * After a port has been determined, calls computeAction() to continue the process. Must be
	 * run in the UI thread!
	 * 
	 * @param event the event which triggered the execution of the VexUploadHandler
	 * @return an IStatus object reflecting the success or failure of port selection (not
	 * necessarily indicative of whether the upload succeeded or failed!)
	 */
	protected IStatus setSerialPort(final ExecutionEvent event) {
		final String portID = port.getPort();
		final IStatus status;
		if (portID != null) {
			// Use port from last time, validated already by port.getPort()
			computeAction(event, portID);
			status = Status.OK_STATUS;
		} else {
			// Get a list, convert to array
			final List<PortFinder.Serial> in = util.locateSerial();
			final PortFinder.Serial[] comms = in.toArray(new PortFinder.Serial[in.size()]);
			if (comms.length > 1) {
				// Display dialog box
				final Object item = EclipseUtils.selectDialog(window, "Select Port",
					"Select VEX communications port:", comms);
				if (item != null) {
					// User selected a port!
					final String target = ((PortFinder.Serial)item).getComIdentifier();
					port.savePort(target);
					computeAction(event, target);
					status = Status.OK_STATUS;
				} else
					// Closed dialog without picking a valid option
					status = Status.CANCEL_STATUS;
			} else if (comms.length > 0) {
				// Only one port, spare the trouble of a dialog
				final String target = comms[0].getComIdentifier();
				port.savePort(target);
				computeAction(event, target);
				status = Status.OK_STATUS;
			} else {
				// No devices found
				EclipseUtils.displayError(window, "Check connection to VEX Cortex",
					"A VEX Programming Kit or USB A-to-A cable was not found.\nEnsure that " +
					"the correct orange USB cable is tightly plugged into this computer.");
				status = Status.CANCEL_STATUS;
			}
		}
		return status;
	}
	/**
	 * Starts the upload process by closing any Terminal views open on the port; then calls
	 * procUpload().
	 * 
	 * @param params the upload parameters computed by computeAction()
	 */
	protected void startUpload(final UploadParams params) {
		// Close all terminal views open
		final UIJob ui = new UIJob("Close terminals") {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				procUpload(params, EclipseUtils.closeTerminals(window, params.getPort()));
				return Status.OK_STATUS;
			}
		};
		ui.setPriority(Job.SHORT);
		ui.schedule();
	}
	/**
	 * Raises an "upload error" message with the given text.
	 * 
	 * @param message the error message to display
	 */
	protected void uploadError(final String message) {
		EclipseUtils.displayError(window, "Upload Error", message);
	}
}