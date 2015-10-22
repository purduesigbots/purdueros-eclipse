package edu.purdue.sigbots.ros.eclipse.flashutil;

import java.util.*;

import com.sun.jna.platform.win32.Win32Exception;

/**
 * A per-flasher class managing saved serial ports.
 */
public class PortPrompter {
	private String saved;

	public PortPrompter() {
		saved = null;
	}

	/**
	 * Verifies the saved port's validity.
	 * 
	 * @return whether the saved port still exists
	 */
	private boolean checkPort() {
		try {
			final List<String> ids = PortFinder.getPortIdentifiers();
			if (saved == null)
				return false;
			for (String id : ids)
				// Simple and stupid
				if (id.equalsIgnoreCase(saved))
					return true;
			return false;
		} catch (Win32Exception e) {
			throw e;
		}

	}

	/**
	 * Gets a reference to the correct COM port.
	 * 
	 * @return the serial port identifier to use, or null if none is configured
	 */
	public String getPort() {
		if (!checkPort())
			saved = null;
		return saved;
	}

	/**
	 * Returns true if a port has been determined.
	 * 
	 * @return whether a port has been remembered
	 */
	public boolean hasSavedPort() {
		if (!checkPort())
			saved = null;
		return saved != null;
	}

	/**
	 * Saves the specified port for later use.
	 * 
	 * @param port
	 *            the port identifier to remember
	 */
	public void savePort(final String port) {
		saved = port;
	}
}