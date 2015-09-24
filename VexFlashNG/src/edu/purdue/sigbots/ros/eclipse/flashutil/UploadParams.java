package edu.purdue.sigbots.ros.eclipse.flashutil;

import java.io.*;

/**
 * Contains all the required parameters for uploading or downloading of data to a VEX device.
 */
public class UploadParams {
	/**
	 * Indicates clean erase and upload code mode.
	 */
	public static final int MODE_CLEAN = 0;
	/**
	 * Indicates partial erase and upload code mode.
	 */
	public static final int MODE_FW = 1;
	/**
	 * Indicates upload file from filesystem mode.
	 */
	public static final int MODE_UPLOAD_FS = 2;
	/**
	 * Indicates download file to filesystem mode.
	 */
	public static final int MODE_DOWNLOAD_FS = 3;

	/**
	 * Operation to perform.
	 */
	protected final int op;
	/**
	 * The port to use for communications.
	 */
	protected final String port;
	/**
	 * The project that is being uploaded. Can be null if not applicable.
	 */
	protected final String project;
	/**
	 * File to upload or download.
	 */
	protected final File target;

	/**
	 * Creates an upload parameters object encapsulating the specified data.
	 * 
	 * @param op the operation to perform (see MODE_xxx constants in this class)
	 * @param port the port to use for communications
	 * @param project the project name, or null if unavailable or not applicable
	 * @param target the file to upload or download
	 */
	public UploadParams(final int op, final String port, final String project,
			final File target) {
		if (port == null)
			throw new NullPointerException("port");
		if (target == null)
			throw new NullPointerException("target");
		if (op < MODE_CLEAN || op > MODE_DOWNLOAD_FS)
			throw new IllegalArgumentException("op");
		this.op = op;
		this.port = port;
		this.project = project;
		this.target = target;
	}
	/**
	 * Gets the description of this operation.
	 * 
	 * @return a friendly description of this operation
	 */
	public String getDescription() {
		final String p = (getProject() != null) ? getProject() : "project";
		switch (getOperation()) {
		case MODE_CLEAN:
			return "Uploading " + p + " to VEX device";
		case MODE_FW:
			return "Uploading " + p + " to VEX device";
		case MODE_UPLOAD_FS:
			return "Retrieving files on VEX device to " + getTarget().getName();
		case MODE_DOWNLOAD_FS:
			return "Sending file " + getTarget().getName() + " to VEX device";
		default:
			// Hush a warning
			break;
		}
		// Sensible default?
		return "Communicating with VEX device";
	}
	/**
	 * Gets the operation to be performed.
	 * 
	 * @return the operation code to perform
	 */
	public int getOperation() {
		return op;
	}
	/**
	 * Gets the port name to be used.
	 * 
	 * @return the port name to use for communications
	 */
	public String getPort() {
		return port;
	}
	/**
	 * Gets the project name.
	 * 
	 * @return the project name, or null if not applicable
	 */
	public String getProject() {
		return project;
	}
	/**
	 * Gets the target file.
	 * 
	 * @return the File to be uploaded or downloaded according to getOperation()
	 */
	public File getTarget() {
		return target;
	}
	public String toString() {
		return String.format("%s[op=%d,port=%s,project=%s,target=%s]",
			getClass().getSimpleName(), op, port, project, target.getPath());
	}
}