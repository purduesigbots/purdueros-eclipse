package edu.purdue.sigbots.ros.eclipse.flashutil;

import com.sun.jna.platform.win32.*;
import jssc.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Find USB COM ports matching certain driver descriptions. Useful for singling
 * out ports based on device type (e.g. Prolific, FTDI, Maple)
 */
public class PortFinder {
	/**
	 * Windows USB enumeration path for FTDI devices
	 */
	private static final String WIN_FTDI_ENUM = "SYSTEM\\CurrentControlSet\\Enum\\FTDIBUS";
	/**
	 * Windows USB enumeration path for most USB devices
	 */
	private static final String WIN_USB_ENUM = "SYSTEM\\CurrentControlSet\\Enum\\USB";
	/**
	 * Pattern to match USB VID:PID combinations from a Windows registry key
	 */
	private static final Pattern WIN_USB_ID = Pattern.compile("VID_([0-9a-f]{4}).+" + "PID_([0-9a-f]{4})",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Finds orphaned ports and sets them straight with HardwareSerial
	 * instances.
	 * 
	 * @param have
	 *            the list of ports that have a match
	 * @param want
	 *            all ports, including the ones not matched
	 */
	private static void cleanupExtras(final List<Serial> have, final List<String> want) {
		for (String unused : want) {
			boolean found = false;
			for (Serial used : have)
				if (unused.equalsIgnoreCase(used.getComPort())) {
					found = true;
					break;
				}
			// If not found, it's a hardware serial
			if (!found)
				have.add(new HardwareSerial(unused));
		}
	}

	/**
	 * Cross-platform method to enumerate all serial ports on the system. This
	 * method will work more often (e.g. on mac) but will not fill in extended
	 * information (all will come back as HardwareSerial instances). Use when
	 * getPortList() throws an exception.
	 *
	 * @return the serial ports available on the system
	 */
	public static List<Serial> defaultPortList() {
		final List<String> ports = getPortIdentifiers();
		final List<Serial> out = new ArrayList<Serial>(ports.size());
		for (String port : ports)
			out.add(new HardwareSerial(port));
		return out;
	}

	/**
	 * Enumerate the serial ports on a Unix/Linux machine given the list of
	 * active COM ports.
	 *
	 * @param coms
	 *            the communication port names available
	 * @return the serial ports found
	 */
	private static List<Serial> enumLinuxCom(final List<String> coms) {
		// Output list
		final List<Serial> out = new ArrayList<Serial>(16);
		final File[] usbDevices = new File("/sys/bus/usb/devices").listFiles();
		for (File usbDevice : usbDevices) {
			// List out all USB devices, find the ones with serial compatibility
			final String uvid = readOneLine(new File(usbDevice, "idVendor"));
			final String upid = readOneLine(new File(usbDevice, "idProduct"));
			String name = readOneLine(new File(usbDevice, "product"));
			if (uvid != null && upid != null) {
				// Potential USB-serial device found
				int vid, pid;
				if (name == null)
					name = "USB Serial Port";
				try {
					// Match VID and PID
					vid = Integer.parseInt(uvid, 16);
					pid = Integer.parseInt(upid, 16);
				} catch (NumberFormatException e) {
					vid = pid = 0;
				}
				// Now look for matching device subdirectory
				final String usbId = usbDevice.getName();
				for (File devDir : usbDevice.listFiles())
					if (devDir.isDirectory() && devDir.getName().contains(usbId))
						// USB driver subdir found
						for (String port : coms) {
							// Strip /dev/
							final String pname = new File(port).getName();
							final File pfile = new File(devDir, "tty");
							if (new File(devDir, pname).exists() || new File(pfile, pname).exists()) {
								out.add(new USBSerial(name, vid, pid, port));
								break;
							}
						}
			}
		}
		cleanupExtras(out, coms);
		return out;
	}

	/**
	 * Enumerate USB devices on the specified enum path.
	 *
	 * @param key
	 *            the path to the enumeration key
	 * @param out
	 *            the list to add devices which were found
	 * @param coms
	 *            the list of communications ports that are plugged in
	 * @param overrideName
	 *            null to use the port's stated name, otherwise this will be
	 *            used
	 */
	private static void enumWindowsCom_(final String key, final List<Serial> out, final List<String> coms,
			final String overrideName) {
		String[] keys;
		// Look for the specified enum key
		try {
			keys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, key);
		} catch (Win32Exception e) {
			keys = new String[0];
		}
		for (String usb : keys)
			try {
				// Look in all subkeys
				final String path = key + "\\" + usb;
				final String[] subKeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, path);
				for (String sk : subKeys) {
					// Friendly name
					String name = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, path + "\\" + sk,
							"FriendlyName");
					// Strip off the last () device
					final int pos = name.lastIndexOf('(');
					if (pos > 0)
						name = name.substring(0, pos).trim();
					// Device Parameters\PortName has the PORT #
					final String com = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE,
							path + "\\" + sk + "\\Device Parameters", "PortName");
					for (String pluggedIn : coms)
						// Is it plugged in?
						if (pluggedIn.equalsIgnoreCase(com)) {
							// usb has the VID & PID
							final Matcher m = WIN_USB_ID.matcher(usb);
							if (m.find()) {
								final int vid = Integer.parseInt(m.group(1), 16);
								final int pid = Integer.parseInt(m.group(2), 16);
								// FTDI comes back as "USB Serial Port", not
								// that helpful
								if (overrideName != null)
									name = overrideName;
								out.add(new USBSerial(name, vid, pid, pluggedIn));
								break;
							}
						}

				}
			} catch (Win32Exception ignore) {
			}
	}

	/**
	 * Enumerates the serial ports on a Windows machine given the list of active
	 * COM ports.
	 *
	 * @param coms
	 *            the communication port names available
	 * @return the serial ports found
	 */
	private static List<Serial> enumWindowsCom(final List<String> coms) {
		// Output list
		final List<Serial> out = new ArrayList<Serial>(16);
		enumWindowsCom_(WIN_USB_ENUM, out, coms, null);
		enumWindowsCom_(WIN_FTDI_ENUM, out, coms, "FTDI USB Device");
		cleanupExtras(out, coms);
		return out;
	}

	/**
	 * Finds all serial ports matching the specified ID. If none are found,
	 * returns null.
	 *
	 * @param id
	 *            the ID to look up
	 * @param input
	 *            the results from getPortList() to check
	 * @return the serial ports which match
	 */
	public static List<Serial> findByID(final String id, final List<Serial> input) {
		final List<Serial> out = new ArrayList<Serial>(16);
		final String m = id.toUpperCase();
		for (Serial ser : input)
			// Either the ID matches or the name matches
			if (ser.getID().toUpperCase().contains(m) || ser.getName().toUpperCase().startsWith(id))
				out.add(ser);
		return out;
	}

	/**
	 * Cross-platform RXTX port enumeration method.
	 *
	 * @return the basic communications port information
	 */
	public static List<String> getPortIdentifiers() {
		// Look for ports; odds are, there's only one these days
		final String[] e = SerialPortList.getPortNames();
		final List<String> out = new ArrayList<String>(e.length);
		Collections.addAll(out, e);
		return out;
	}

	/**
	 * Cross-platform method to enumerate all serial ports on the system.
	 *
	 * @return the serial ports available on the system
	 */
	public static List<Serial> getPortList() {
		final List<String> ports = getPortIdentifiers();
		final String os = System.getProperty("os.name", "nix").toLowerCase();
		// OS-specific
		if (os.startsWith("win"))
			return enumWindowsCom(ports);
		else if (os.startsWith("mac"))
			throw new UnsupportedOperationException("No extended serial on OSX yet");
		return enumLinuxCom(ports);
	}

	/**
	 * Reads and returns the first line of the given file.
	 *
	 * @param input
	 *            the file to read
	 * @return the first line, or null if the file is empty or inaccessible
	 */
	private static String readOneLine(final File input) {
		try {
			// Read in one line from the file; if it's empty, it gets NULL which
			// is intended
			final BufferedReader br = new BufferedReader(new FileReader(input));
			final String s = br.readLine();
			br.close();
			return s;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Represents an extended serial device.
	 */
	public static interface Serial {
		/**
		 * Gets the communication port identifier used to open this device.
		 *
		 * @return the communication port identifier for opening a serial
		 *         connection
		 */
		public String getComIdentifier();

		/**
		 * Gets the serial port's communications name (specific to OS).
		 *
		 * @return the unfriendly name of the port
		 */
		public String getComPort();

		/**
		 * Gets the serial port identifier. For hardware ports, this will return
		 * the same value as getComPort(). USB and other extended devices will
		 * return a more specific identifier good for identifying devices.
		 *
		 * @return the port ID
		 */
		public String getID();

		/**
		 * Gets the serial port's friendly name.
		 *
		 * @return the serial port friendly name
		 */
		public String getName();
	}

	/**
	 * Represents a hardware serial device.
	 */
	public static class HardwareSerial implements Serial {
		private final String comPort;

		public HardwareSerial(final String comPort) {
			this.comPort = comPort;
		}

		public String getComIdentifier() {
			return comPort;
		}

		public String getComPort() {
			return comPort;
		}

		public String getID() {
			return getComPort();
		}

		private String getType() {
			final String comPortLC = comPort.toLowerCase();
			if (comPortLC.indexOf("usb") >= 0 || comPort.indexOf("acm") >= 0)
				return "USB Serial";
			else if (comPort.indexOf("bt") >= 0)
				return "Bluetooth Serial";
			else
				return "Hardware Serial";
		}

		public String getName() {
			return getType() + " " + comPort;
		}

		public String toString() {
			return getComPort() + " (" + getType() + ")";
		}
	}

	/**
	 * Represents a USB-serial device.
	 */
	public static class USBSerial implements Serial {
		private final String comPort;
		private final String name;
		private final int pid;
		private final int vid;

		public USBSerial(final String name, final int vid, final int pid, final String com) {
			this.comPort = com;
			this.name = name;
			this.pid = pid;
			this.vid = vid;
		}

		public String getComIdentifier() {
			return comPort;
		}

		public String getComPort() {
			return comPort;
		}

		public String getName() {
			return name;
		}

		public String getID() {
			return String.format("%04X:%04X", vid, pid);
		}

		public String toString() {
			return getComPort() + " (" + name + " " + getID() + ")";
		}
	}
}