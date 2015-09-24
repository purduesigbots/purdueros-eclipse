package edu.purdue.sigbots.ros.eclipse.flashutil;

import jssc.*;
import java.io.*;
import java.util.*;

/**
 * VEX Flash program, loosely based off of stm32flash (http://stm32flash.googlecode.com/)
 *
 * Reflashes a Vex Cortex microcontroller with user code.
 */
public class VexFlash implements FlashUtility {
	/**
	 * Default baud rate to use for flashing. At present this can't be changed at runtime.
	 */
	public static final int BAUD = 115200;
	/**
	 * FS start address relative to state.getUserCodeAddress().
	 */
	public static final int FS_START = 128 * 1024;
	/**
	 * Sequence to send to the VexNET system to reset Cortex into bootloader mode.
	 */
	private static final short[] BOOTLOAD = { 0xC9, 0x36, 0xB8, 0x47, 0x25 };
	/**
	 * Sequence to send to the VexNET system to kill user code and return to firmware.
	 */
	private static final short[] STOP_USER_CODE = { 0x0F, 0x0F, 0x21, 0xDE, 0x08, 0x00, 0x00,
		0x00, 0x08, 0xF1, 0x04 };
	/**
	 * Asks for system information to determine the power levels and connection type
	 */
	private static final short[] SYSINFO = { 0xC9, 0x36, 0xB8, 0x47, 0x21 };
	/**
	 * The timeout value in milliseconds used on the serial port while uploading.
	 */
	public static final long VEX_TIMEOUT = 700L;

	/**
	 * Asks the Cortex/joystick for the current status.
	 *
	 * @param port the serial port to use
	 * @throws SerialException if an I/O error occurs
	 */
	private static void askSysInfo(final SerialPortIO port) throws SerialException {
		try {
			parityNone(port);
			for (short value : SYSINFO)
				port.write(value & 0xFF);
			port.flush();
		} catch (IOException e) {
			throw getNotRespondingException(e);
		}
	}
	/**
	 * Factory method for generic Cortex not responding message.
	 * 
	 * @param cause the cause of this error
	 * @return the error text
	 */
	private static SerialException getNotRespondingException(final IOException cause) {
		return new SerialException("The VEX Joystick or VEX Cortex is not responding.\n" +
			"Ensure that the VEX Programming Kit or USB tether is tightly connected and " +
			"that the VEX devices are powered on.", cause);
	}
	/**
	 * Factory method for generic port in use message.
	 * 
	 * @param bad the port that is closed
	 * @param cause the cause of this error
	 * @return the error text
	 */
	private static SerialException getPortLockedException(final String bad,
			final Exception cause) {
		return new SerialException("The selected communications port cannot be opened.\n" +
			"Close any terminal emulators or competition switch simulators which are " +
			"using " + bad + ", and ensure that the current user has the required " +
			"permissions to use serial devices.\n", cause);
	}
	/**
	 * Kills the currently running user code.
	 *
	 * @param port the serial port to use
	 * @throws SerialException if an I/O error occurs
	 */
	private static void killUserCode(final SerialPortIO port) throws SerialException {
		long now;
		try {
			paritySTM(port);
			// Write these out, one by one
			for (short value : STOP_USER_CODE) {
				now = System.currentTimeMillis();
				port.write(value & 0xFF);
				if (System.currentTimeMillis() - now > (VEX_TIMEOUT - 50L))
					// Sometimes, the serial port will lock up but not throw an error...
					throw getNotRespondingException(null);
				Utils.delay(100L);
			}
			port.flush();
		} catch (IOException e) {
			throw getNotRespondingException(e);
		}
	}
	/**
	 * Sets up the port for no parity and the default data rate.
	 *
	 * @param port the port to use
	 * @throws SerialException if an I/O error occurs
	 */
	private static void parityNone(final SerialPortIO port) throws SerialException {
		try {
			port.flush();
			port.setParams(BAUD, SerialPort.PARITY_NONE);
			port.purge();
		} catch (IOException e) {
			throw getPortLockedException(port.getName(), e);
		}
	}
	/**
	 * Sets up the port for STM even parity and data rate.
	 *
	 * @param port the port to use
	 * @throws SerialException if an I/O error occurs
	 */
	private static void paritySTM(final SerialPortIO port) throws SerialException {
		try {
			port.flush();
			port.setParams(BAUD, SerialPort.PARITY_EVEN);
			port.purge();
		} catch (IOException e) {
			throw getPortLockedException(port.getName(), e);
		}
	}
	/**
	 * Returns VexNET into terminal mode.
	 *
	 * @param port the serial port to use
	 */
	private static void resetVexNET(final SerialPortIO port) {
		try {
			parityNone(port);
			// Put VexNET into terminal mode
			port.write(20);
			port.flush();
		} catch (Exception ignore) { }
	}
	/**
	 * Initializes VexNET to prepare a Cortex communication.
	 *
	 * @param port the serial port to use
	 * @throws SerialException if an I/O error occurs
	 */
	private static void vexInit(final SerialPortIO port) throws SerialException {
		try {
			parityNone(port);
			for (int i = 0; i < 5; i++) {
				// Write the bootload sequence several times
				for (short value : BOOTLOAD)
					port.write(value & 0xFF);
				port.flush();
				Utils.delay(150L);
			}
		} catch (Exception e) {
			throw new SerialException("Failed to initialize controller", e);
		}
	}

	/**
	 * Input file data.
	 */
	private Parser fileData;
	/**
	 * The input (output) file, used for FS upload/download to get the target name.
	 */
	private File file;
	/**
	 * Current programming mode.
	 */
	private int mode;
	/**
	 * The serial port in use.
	 */
	private SerialPortIO port;
	/**
	 * Current state of the attached STM microcontroller.
	 */
	private STMState state;

	/**
	 * Waits for reset and reconnects.
	 *
	 * @param output the indicator for status messages
	 * @throws SerialException if an I/O error occurs
	 */
	private void connect(final Indicator output) throws SerialException {
		// Kill user code
		output.messageBegin("Stopping user code");
		killUserCode(port);
		output.messageEnd("done.");
		Utils.delay(100L);
		// Initialize VEX system
		output.messageBegin("Interrogating VEX system");
		getSystemInformation();
		output.messageEnd("done.");
		Utils.delay(100L);
		output.messageBegin("Initializing controller");
		vexInit(port);
		output.messageEnd("done.");
		Utils.delay(400L);
		// Initialize STM connection
		stmInit();
	}
	public void end() {
		if (fileData != null)
			fileData.close();
		if (port != null)
			port.close();
	}
	/**
	 * Erases the Cortex memory.
	 *
	 * @param output the indicator for status messages
	 * @throws SerialException if an I/O error occurs
	 */
	private void eraseAll(final Indicator output) throws SerialException {
		output.messageBegin("Erasing memory");
		state.commandER();
		output.messageEnd("done.");
		Utils.delay(100);
	}
	/**
	 * Erases the first few pages of Cortex flash memory (all pages before FS_START)
	 *
	 * @param fs the file system manipulator pointing to the port
	 * @param output the indicator for status messages
	 * @throws SerialException if an I/O error occurs
	 */
	private void eraseSome(final FileSystemManipulator fs, final Indicator output)
			throws SerialException {
		output.messageBegin("Erasing memory");
		// Order FS to erase the pages
		fs.eraseRange(0, FS_START / state.getDevice().getPageSize() - 1);
		output.messageEnd("done.");
		Utils.delay(100);
	}
	public String getExtension() {
		return "bin";
	}
	/**
	 * Gets information from the Cortex to determine its connection type and power level.
	 * Currently, the data is ignored, but it will be displayed once the meaning is known.
	 *
	 * @throws SerialException if an I/O error occurs
	 */
	private void getSystemInformation() throws SerialException {
		try {
			askSysInfo(port);
			Utils.readExactly(port, 14);
		} catch (IOException e) {
			throw getNotRespondingException(e);
		}
	}
	public List<PortFinder.Serial> locateSerial() {
		final List<PortFinder.Serial> candidates = new ArrayList<PortFinder.Serial>(8);
		try {
			// Add Prolific (067B) and VEX direct (04D8) ports
			final List<PortFinder.Serial> all = PortFinder.getPortList();
			candidates.addAll(PortFinder.findByID("067B", all));
			candidates.addAll(PortFinder.findByID("04D8", all));
		} catch (RuntimeException e) {
			// Default port list
			candidates.clear();
			candidates.addAll(PortFinder.defaultPortList());
			// Delete elements with "btcomm" or "ttyS" in the name for Mac
			for (final Iterator<PortFinder.Serial> it = candidates.iterator(); it.hasNext(); ) {
				String s = it.next().getName();
				if (s != null && (s = s.toLowerCase()).indexOf("btcomm") >= 0 &&
					s.indexOf("ttys") >= 0) it.remove();
			}
		}
		return candidates;
	}
	public void program(final Indicator output) throws SerialException {
		connect(output);
		final FileSystemManipulator fs = new FileSystemManipulator(state);
		try {
			switch (mode) {
			case UploadParams.MODE_CLEAN:
			case UploadParams.MODE_FW:
				// Erase memory
				if (mode == UploadParams.MODE_CLEAN)
					eraseAll(output);
				else
					eraseSome(fs, output);
				// Program memory
				fs.writeDataToAddress(0, null, fileData, output);
				break;
			case UploadParams.MODE_DOWNLOAD_FS:
				// FS download
				fs.download(file.getName(), fileData, output);
				break;
			case UploadParams.MODE_UPLOAD_FS:
				// FS upload
				fs.uploadAllFiles(file, output);
				break;
			default:
				// Do nothing
				break;
			}
		} catch (IOException e) {
			// This occurs on local FS errors only
			throw new SerialException("Could not read or write file on local computer.\n" +
				"Ensure that the selected file or directory is accessible by this user.", e);
		} finally {
			// Make sure that VEXnet enters terminal mode
			restartCode(output);
			resetVexNET(port);
		}
	}
	public boolean requiresSerial() {
		return true;
	}
	/**
	 * Restarts the user code.
	 *
	 * @param output the indicator for status messages
	 * @throws SerialException if an I/O error occurs
	 */
	private void restartCode(final Indicator output) throws SerialException {
		output.message("Starting user code");
		state.commandGO(state.getUserCodeAddress());
		// No verify, the bootloader has just jumped to user code
		Utils.delay(100L);
	}
	public boolean setup(final UploadParams params) throws SerialException {
		file = params.getTarget();
		mode = params.getOperation();
		// Read in file
		if (mode == UploadParams.MODE_UPLOAD_FS)
			fileData = null;
		else
			// Try to open the input file
			try {
				fileData = new BinaryParser(file);
			} catch (IOException e) {
				throw new SerialException("Error reading from " + file.getAbsolutePath(), e);
			}
		// Open streams
		final String portName = params.getPort();
		try {
			this.port = Utils.openSerialPort(portName);
			this.port.setTimeout(VEX_TIMEOUT);
		} catch (SerialException e) {
			throw getPortLockedException(portName, e);
		}
		return true;
	}
	/**
	 * Initializes the Cortex connection.
	 *
	 * @throws SerialException if an I/O error occurs during initialization
	 */
	private void stmInit() throws SerialException {
		// Switch to STM even parity
		paritySTM(port);
		Utils.eat(port);
		try {
			// Initialize STM
			state = new STMState(port);
			state.negotiate();
			// Get command set and ID information
			state.commandGET();
			state.commandGID();
		} catch (SerialException e) {
			// Failed to init
			throw new SerialException("The VEX Cortex is not responding to initialization.\n" +
				"Ensure that the USB Tether cable or VEXnet keys are tightly plugged in,\n" +
				"and that all VEX devices are powered on.", e);
		}
	}
}