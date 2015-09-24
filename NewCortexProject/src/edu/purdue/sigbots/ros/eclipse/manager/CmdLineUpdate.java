package edu.purdue.sigbots.ros.eclipse.manager;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accepts a directory on the command line and updates the PROS project in that directory,
 * if possible, to the latest version of PROS.
 */
public class CmdLineUpdate {
	public static final String[] UPGRADE_FILES = new String[] {
		"firmware/libccos.a",
		"firmware/uniflash.jar",
		"include/API.h",
		"src/Makefile",
		"Makefile"
	};
	public static final String PROS_VERSION_REGEX = "([0-9]{1,2}[abro][0-9]{2})";
	private static final Pattern PROS_VERSION = Pattern.compile("PROS " + PROS_VERSION_REGEX);
	private static String prosVersion = null;

	public static InputStream openStream(String path) throws FileNotFoundException {
		InputStream stream = CmdLineUpdate.class.getResourceAsStream("/sample/" + path);
		if(stream == null) throw new FileNotFoundException("Could not find the file " + path + " in the sample project directory.");
		return stream;
		
	}
	private static void updatePROSVersion() throws FileNotFoundException {
		// Read in the PROS binary and look for "PROS XXXX"
		final InputStream is = openStream("firmware/libccos.a");
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
	public static synchronized String getPROSVersion() throws FileNotFoundException {
		if (prosVersion == null)
			updatePROSVersion();
		return prosVersion;
	}

	public static void main(String[] args) throws FileNotFoundException {
		final String v = getPROSVersion();
		if (args.length < 1) {
			// No arguments entered?
			System.err.println("Usage: update-pros directory");
			System.err.println("\twhere DIRECTORY is the path to the PROS project to update");
			if (v != null && v.length() > 0)
				System.err.println("\tThis will update the project to PROS " + v);
		} else {
			new CmdLineUpdate().update(args[0]);
		}
	}

	public void update(final String path) {
		final File file;
		try {
			file = new File(path).getCanonicalFile();
			// Invalid directory entered?
			if (!file.isDirectory() || !file.canRead())
				throw new IOException("Error reading directory " + path);
		} catch (IOException e) {
			System.err.println("Failed to open directory \"" + path + "\" for updating");
			return;
		}
		try {
			if (!new File(file, "firmware").isDirectory())
				// Not a PROS project?
				System.err.println("The directory \"" + path +
					"\" does not appear to contain a PROS project.");
			else {
				final String v = getPROSVersion();
				if (v != null && v.length() > 0)
					System.out.println("Updating \"" + path + "\" to PROS version " + v);
				else
					System.out.println("Updating \"" + path + "\"");
				final byte[] buffer = new byte[1024]; int n;
				for (String fn : UPGRADE_FILES) {
					// Calculate target
					final File target = new File(file, fn).getCanonicalFile();
					final InputStream is = openStream(fn);
					// Check for error opening JAR
					if (is == null)
						throw new IOException("When updating file " + fn);
					// Write content to file
					final OutputStream os = new BufferedOutputStream(
						new FileOutputStream(target), 1024);
					// Block copy, 1K bytes at a time
					while ((n = is.read(buffer)) > 0)
						os.write(buffer, 0, n);
					is.close();
					os.close();
					System.out.println("Wrote file " + fn + "...");
				}
				System.out.println("Update complete!");
			}
		} catch (IOException e) {
			System.err.println("Failed to update PROS project \"" + path + "\"");
		}
	}
}
