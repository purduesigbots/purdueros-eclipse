package edu.purdue.sigbots.ros.eclipse.manager.menus;

import java.io.*;
import java.net.*;
import java.util.regex.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.ui.progress.UIJob;

import edu.purdue.sigbots.ros.eclipse.manager.CmdLineUpdate;

public class UpdateChecker implements IStartup {
	// The URL to check for updates
	public static final String UPDATE_CHECK_URL =
		"http://sourceforge.net/projects/purdueros/files/list";

	/**
	 * Reads the file from the input stream info memory.
	 * 
	 * @param is the stream to read
	 * @return the text read
	 * @throws IOException if an I/O error occurs
	 */
	private static StringBuilder readDataFrom(final InputStream is) throws IOException {
		final BufferedReader br = new BufferedReader(new InputStreamReader(is),
			16384);
		final StringBuilder result = new StringBuilder(16384);
		try {
			// Pull file into memory up to 10K limit
			String line;
			while ((line = br.readLine()) != null && (result.length() + 
					line.length()) < 163840)
				result.append(line);
		} finally {
			br.close();
		}
		return result;
	}
	/**
	 * Notifies the user of update availability.
	 * 
	 * @param oldV the old version
	 * @param newV the new available version
	 */
	private static void notifyUpdateAvailable(final String oldV, final String newV) {
		final UIJob job = new UIJob("Update Available") {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				final IWorkbenchWindow window = PlatformUI.getWorkbench().
					getActiveWorkbenchWindow();
				// Tell the user (TODO allow user to hush warnings!?)
				MessageDialog.openInformation(window.getShell(), "PROS Update Available",
					"PROS version " + newV + " (local version " + oldV + ") is available.\n" +
					"Updates to PROS may contain important fixes or new features.\n" +
					"Individual projects can keep older versions of PROS if necessary.");
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
	/**
	 * Performs the update check against our current PROS version.
	 * 
	 * @param result the results from the website
	 * @return the newest version for updates, or null if none available
	 */
	private static String runUpdateCheck(final CharSequence result) throws FileNotFoundException {
		final String ourVersion = CmdLineUpdate.getPROSVersion();
		String updateVersion = ourVersion;
		// Are we release?
		final boolean isRelease = ourVersion.indexOf('r') > 0;
		int major = 0, minor = 0;
		// Extract version (really can't fail but...)
		try {
			final int len = ourVersion.length();
			minor = Integer.parseInt(ourVersion.substring(len - 2, len));
			major = Integer.parseInt(ourVersion.substring(0, len - 3));
		} catch (NumberFormatException ignore) { }
		// Scan the string for "valid PROS version":
		final Matcher m = Pattern.compile("\"" + CmdLineUpdate.PROS_VERSION_REGEX + "\":").
			matcher(result);
		while (m.find()) {
			// Extract group = PROS version
			final String v = m.group(1).toLowerCase();
			if (!isRelease || v.indexOf('r') > 0) {
				// If we are on beta channel and anything available, or we are
				// on release channel and release available
				int newMinor = 0, newMajor = 0;
				try {
					final int len = v.length();
					newMinor = Integer.parseInt(v.substring(len - 2, len));
					newMajor = Integer.parseInt(v.substring(0, len - 3));
				} catch (NumberFormatException ignore) { }
				// Ensure we keep going if parse failure (which can't happen)
				if (newMajor > major || (newMajor == major && newMinor > minor)) {
					// Update available
					major = newMajor;
					minor = newMinor;
					updateVersion = v;
				}
			}
		}
		// If no update available, make version null
		if (updateVersion.equalsIgnoreCase(ourVersion))
			updateVersion = null;
		return updateVersion;
	}
	/**
	 * Checks for PROS updates.
	 */
	private static void updateCheck() {
		final Job updateJob = new Job("Check for PROS updates") {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					final URL testUrl = new URL(UPDATE_CHECK_URL);
					// Connect to sourceforge
					final HttpURLConnection conn = (HttpURLConnection)testUrl.openConnection();
					conn.setInstanceFollowRedirects(true);
					conn.setConnectTimeout(1000);
					conn.setReadTimeout(1000);
					conn.connect();
					// Check response code
					if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
						// Grab input stream reference
						final String v = runUpdateCheck(readDataFrom(conn.getInputStream()));
						if (v != null)
							notifyUpdateAvailable(CmdLineUpdate.getPROSVersion(), v);
					}
					conn.disconnect();
				} catch (Exception ignore) {
					// Anything that goes wrong during update check should be ignored
					// While it's important that users run the latest PROS, we don't want to
					// raise an error dialog box on offline computers
					// Logging it won't do us any good since most users will never check the log
				}
				return Status.OK_STATUS;
			}
		};
		updateJob.schedule();
	}

	public void earlyStartup() {
		updateCheck();
	}
}
