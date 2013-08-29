package com.purduesigbots.vexflash;

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
		return new SerialException("VEX Joystick or tethered VEX Cortex is not responding.\n" +
			"Ensure that VEX Programming Kit is tightly connected and that the " +
			"VEX Joystick is powered on.", cause);
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
		return new SerialException("Selected communications port cannot be opened.\n" +
			"Close any terminal emulators or competition switch replacements which are " +
			"using " + bad + ", and ensure that the current user has " +
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
				if (System.currentTimeMillis() - now > 450L)
					// Sometimes, the serial port will lock up but not throw an error...
					throw getNotRespondingException(null);
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
				for (short value : BOOTLOAD)
					port.write(value & 0xFF);
				port.flush();
				Utils.delay(50);
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
		Utils.delay(50);
		// Initialize VEX system
		output.messageBegin("Interrogating VEX system");
		getSystemInformation();
		output.messageEnd("done.");
		output.messageBegin("Initializing controller");
		vexInit(port);
		output.messageEnd("done.");
		Utils.delay(400);
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
	public String getExtension() {
		return "bin";
	}
	/**
	 * Gets information from the Cortex to determine its connection type and power level.
	 * Currently, the data is trashed, but it will be displayed once the meaning is known.
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
		// Fetch input and output streams
		// Erase memory
		eraseAll(output);
		// Program memory
		reflash(output);
		restartCode(output);
		resetVexNET(port);
	}
	private void reflash(final Indicator output) throws SerialException {
		int len, offset = 0;
		// Get start address
		final int addr = state.getUserCodeAddress(), size = fileData.length(),
			flashSize = state.getFlashSize();
		final byte[] buffer = new byte[256];
		// Too big?
		if (size >= flashSize)
			throw new SerialException(String.format("Program is too big to fit in memory.\n" +
				"Selected program is %d KiB out of %d KiB", size / 1024,
				flashSize / 1024));
		output.begin();
		try {
			while (offset < size && (len = fileData.read(buffer, buffer.length)) > 0) {
				// Fill buffer with alignment padding
				for (int i = len; i < buffer.length; i++)
					buffer[i] = (byte)0xFF;
				// Send write command
				try {
					state.commandWM(addr + offset, buffer);
					Utils.delay(10);
				} catch (SerialException e) {
					// Wait 1s for reconnect
					Utils.delay(1000);
					// Flush buffers
					Utils.eat(port);
					// Reinitialize the controller
					connect(output);
					// If we got some stuff OK, then restart flashing from this address
					state.commandWM(addr + offset, buffer);
				}
				offset += len;
				output.progress(100 * offset / size);
			}
			output.end();
		} catch (Exception e) {
			// Programming error!
			output.end();
			throw new SerialException("Connection lost to VEX Cortex while uploading.\n" +
				"If this error frequently recurs, try another set of VEXnet keys, " +
				"or use the USB Tether cable.", e);
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
		Utils.delay(100);
	}
	public boolean setup(final File file, final String[] args, final String port)
			throws SerialException {
		// Read in file
		try {
			fileData = new BinaryParser(file);
		} catch (IOException e) {
			throw new SerialException("Error reading from file " + file.getAbsolutePath(), e);
		}
		// Open streams
		try {
			this.port = Utils.openSerialPort(port);
			this.port.setTimeout(500L);
		} catch (SerialException e) {
			throw getPortLockedException(port, e);
		}
		return true;
	}
	/**
	 * Initializes the Cortex connection.
	 *
	 * @throws SerialException if an I/O error occurs during initialization
	 */
	private void stmInit() throws SerialException {
		STMDevice device = null;
		try {
			// Switch to STM even parity
			paritySTM(port);
			Utils.eat(port);
			// Initialize STM
			state = new STMState(port);
			state.negotiate();
			// Get command set and ID information
			state.commandGET();
			state.commandGID();
			device = state.getDevice();
		} catch (Exception e) { }
		// Failed to init
		if (device == null)
			throw new SerialException("VEX Cortex is not responding to initialization.\n" +
				"Check that VEXnet keys or USB Tether cable are plugged in.\n" +
				"Ensure the VEX Cortex is powered on.");
	}
}