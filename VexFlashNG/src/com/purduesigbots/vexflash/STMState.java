package com.purduesigbots.vexflash;

import java.io.*;

/**
 * Represents the current STM state.
 */
public class STMState {
	/**
	 * How many times to retry on EOF during verification.
	 */
	private static final int RETRIES = 5;

	/**
	 * The current bootloader version.
	 */
	private int bootloaderVersion;
	/**
	 * Command for erasing memory.
	 */
	private int cmdER;
	/**
	 * Command for getting device identifier.
	 */
	private int cmdGID;
	/**
	 * Command for starting execution at a specified address.
	 */
	private int cmdGO;
	/**
	 * Command for getting the device version and option bytes.
	 */
	private int cmdGVR;
	/**
	 * Command for reading memory.
	 */
	private int cmdRD;
	/**
	 * Command for enabling read protection.
	 */
	private int cmdRP;
	/**
	 * Command for disabling read protection.
	 */
	private int cmdUR;
	/**
	 * Command for disabling write protection.
	 */
	private int cmdUW;
	/**
	 * Command for writing memory.
	 */
	private int cmdWM;
	/**
	 * Command for enabling write protection.
	 */
	private int cmdWP;
	/**
	 * Device specification, including memory sizes and identifier.
	 */
	private STMDevice device;
	/**
	 * Option byte #1.
	 */
	private byte option1;
	/**
	 * Option byte #2.
	 */
	private byte option2;
	/**
	 * The port to and from the device.
	 */
	private SerialPortIO port;
	/**
	 * Device version #.
	 */
	private int version;

	/**
	 * Creates an STM32 object on the given serial port.
	 *
	 * @param port the serial port to and from the device
	 */
	public STMState(final SerialPortIO port)  {
		// Initialize
		device = null;
		this.port = port;
	}
	/**
	 * Sends a command to the STM32 device.
	 *
	 * @param command the command code to send
	 * @throws SerialException if an I/O error occurs
	 */
	public void command(final int command) throws SerialException {
		try {
			port.write(new byte[] { (byte)command, (byte)~command });
			port.flush();
			verify();
		} catch (IOException e) {
			throw new SerialException("Error sending command " + command, e);
		}
	}
	/**
	 * Erases all flash.
	 * 
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandER() throws SerialException {
		command(cmdER);
		command(0xFF);
	}
	/**
	 * Erases selected pages of flash.
	 *
	 * @param pages the pages to erase
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandER(final byte[] pages) throws SerialException {
		final int len = pages.length;
		command(cmdER);
		try {
			final byte[] newpages = new byte[len + 2];
			// # of sectors in list
			newpages[0] = (byte)(len - 1);
			// Pages to erase
			System.arraycopy(pages, 0, newpages, 1, len);
			// Write checksum and verify
			newpages[len + 1] = Utils.checksum(newpages[0], pages, 0, len);
			port.write(newpages);
			port.flush();
		} catch (IOException e) {
			throw new SerialException("Failed to erase memory", e);
		}
		verify();
	}
	/**
	 * Gets the supported bootloader commands and version. Must be the first command,
	 * other than a possible call to negotiate()
	 *
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandGET() throws SerialException {
		// Send GET command
		command(0x00);
		// Read length, then read supported commands
		final byte[] data;
		try {
			final int count = Utils.readOne(port) + 1;
			data = Utils.readExactly(port, count);
		} catch (IOException e) {
			throw new SerialException("Failed to read bootloader command set", e);
		}
		bootloaderVersion = (data[0] & 0xFF);
		// Assign commands
		//cmdGET = (data[1] & 0xFF);
		cmdGVR = (data[2] & 0xFF);
		cmdGID = (data[3] & 0xFF);
		cmdRD = (data[4] & 0xFF);
		cmdGO = (data[5] & 0xFF);
		cmdWM = (data[6] & 0xFF);
		cmdER = (data[7] & 0xFF);
		cmdWP = (data[8] & 0xFF);
		cmdUW = (data[9] & 0xFF);
		cmdRP = (data[10] & 0xFF);
		cmdUR = (data[11] & 0xFF);
		verify();
	}
	/**
	 * Gets the ID information of this device.
	 * 
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandGID() throws SerialException {
		STMDevice newDevice = null;
		command(cmdGID);
		try {
			// Read length, then version bytes
			final int len = Utils.readOne(port) + 1;
			final byte[] verData = Utils.readExactly(port, len);
			if (len == 2) {
				final short pid = (short)((verData[0] & 0xFF) << 8 | (verData[1] & 0xFF));
				// Look for device PID in specified array
				for (STMDevice dev : STMDevice.STM_DEVICES)
					if (dev.getID() == pid) {
						newDevice = dev;
						break;
					}
				if (newDevice == null)
					// Perhaps a new Cortex has emerged?
					throw new SerialException(String.format("Unsupported device - PID %4X",
						(pid & 0xFFFF)));
				else
					device = newDevice;
			} else
				// Maybe someday...
				throw new SerialException("Unsupported device - " + len + " PID bytes");
		} catch (IOException e) {
			throw new SerialException("Failed to read device information", e);
		}
		verify();
	}
	/**
	 * Starts execution at the specified unsigned 32-bit address.
	 *
	 * @param address the address to use
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandGO(final int address) throws SerialException {
		command(cmdGO);
		try {
			port.write(Utils.memAddress(address));
		} catch (IOException e) {
			throw new SerialException("Error when starting execution", e);
		}
	}
	/**
	 * Gets the chip version and option bytes.
	 *
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandGVR() throws SerialException {
		command(cmdGVR);
		// Version, option byte #1, option byte #2
		try {
			final byte[] ver = Utils.readExactly(port, 3);
			version = ver[0] & 0xFF;
			option1 = ver[1];
			option2 = ver[2];
		} catch (IOException e) {
			throw new SerialException("Error when retrieving version and options", e);
		}
		verify();
	}
	/**
	 * Reads bytes from memory.
	 *
	 * @param start the starting address
	 * @param length the number of bytes to read
	 * @return the bytes read
	 * @throws SerialException if an I/O error occurs
	 */
	public byte[] commandRD(final int start, final int length) throws SerialException {
		final int len = length - 1;
		command(cmdRD);
		try {
			// Write starting address (& checksum)
			port.write(Utils.memAddress(start));
			port.flush();
			verify();
			// # of bytes and checksum
			port.write(new byte[] { (byte)len, (byte)~len });
			port.flush();
			verify();
			// Read data from BL
			return Utils.readExactly(port, length);
		} catch (IOException e) {
			throw new SerialException("Error when reading memory", e);
		}
	}
	/**
	 * Enables read protection. This will reset the MCU! Re-negotiation is necessary.
	 * 
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandRP() throws SerialException {
		command(cmdRP);
		verify();
	}
	/**
	 * Disables read protection. This will reset the MCU and erase ALL flash! Re-negotiation 
	 * is necessary.
	 *
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandUR() throws SerialException {
		command(cmdUR);
		verify();
	}
	/**
	 * Disables write protection.
	 * 
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandUW() throws SerialException {
		command(cmdUW);
		verify();
	}
	/**
	 * Writes memory to the chip.
	 *
	 * @param start the starting address to write to
	 * @param data the data to write
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandWM(final int start, final byte[] data) throws SerialException {
		// Maximum write size is 256 bytes, must be aligned, etc.
		final int len = data.length;
		if (len < 4 || len > 256 || len % 4 != 0)
			throw new IllegalArgumentException("Must be word-aligned data, 4-256 bytes");
		command(cmdWM);
		try {
			final byte[] newdata = new byte[len + 2];
			// Write starting address (& checksum)
			port.write(Utils.memAddress(start));
			port.flush();
			verify();
			// Write length
			newdata[0] = (byte)(len - 1);
			// Write data
			System.arraycopy(data, 0, newdata, 1, len);
			// Write checksum and verify
			newdata[len + 1] = Utils.checksum(newdata[0], data, 0, len);
			port.write(newdata);
			port.flush();
			verify();
		} catch (IOException e) {
			throw new SerialException("Error when programming memory", e);
		}
	}
	/**
	 * Enables write protection.
	 *
	 * @param sectors the sectors to protect
	 * @throws SerialException if an I/O error occurs
	 */
	public void commandWP(final byte[] sectors) throws SerialException {
		final int len = sectors.length;
		command(cmdWP);
		try {
			final byte[] newsectors = new byte[len + 2];
			// Length, then sectors to protect
			newsectors[0] = (byte)(len - 1);
			System.arraycopy(sectors, 0, newsectors, 1, len);
			// Write checksum and verify
			newsectors[len + 1] = Utils.checksum(newsectors[0], sectors, 0, len);
			port.write(newsectors);
			port.flush();
		} catch (IOException e) {
			throw new SerialException("Error when write-protecting chip", e);
		}
		verify();
	}
	/**
	 * Gets the bootloader version of this device.
	 * 
	 * @return the device bootloader version
	 */
	public int getBootloaderVersion() {
		return bootloaderVersion;
	}
	/**
	 * Gets the device information structure.
	 *
	 * @return the structure with device information, or null if no commandGID() has been
	 * performed successfully
	 */
	public STMDevice getDevice() {
		return device;
	}
	/**
	 * Gets the current serial port.
	 * 
	 * @return the currently connected serial port
	 */
	public SerialPortIO getPort() {
		return port;
	}
	/**
	 * Gets the device's Flash size.
	 *
	 * @return the size of the Flash array
	 */
	public int getFlashSize() {
		if (device == null)
			throw new IllegalStateException("Must use commandGID() before using this method");
		return device.getFlashSize();
	}
	/**
	 * Gets the device's RAM size.
	 *
	 * @return the size of the SRAM array
	 */
	public int getRAMSize() {
		if (device == null)
			throw new IllegalStateException("Must use commandGID() before using this method");
		return device.getRAMSize();
	}
	/**
	 * Gets the address where user code should be stored.
	 *
	 * @return the user code address (typically Flash start)
	 */
	public int getUserCodeAddress() {
		if (device == null)
			throw new IllegalStateException("Must use commandGID() before using this method");
		return getDevice().getFlashStart();
	}
	/**
	 * Gets the value of option byte #1.
	 *
	 * @return the first option byte retrieved by commandGVR()
	 */
	public byte getOption1() {
		return option1;
	}
	/**
	 * Gets the value of option byte #2.
	 *
	 * @return the second option byte retrieved by commandGVR()
	 */
	public byte getOption2() {
		return option2;
	}
	/**
	 * Gets the device version.
	 *
	 * @return the device version retrieved by commandGVR()
	 */
	public int getVersion() {
		return version;
	}
	/**
	 * Negotiates with the chip to auto-set the baud rate. Chip must be straight out of reset!
	 * This won't work in the middle of a programming sequence!
	 * 
	 * @return whether negotiation was successful
	 * @throws SerialException if an I/O error occurs
	 */
	public boolean negotiate() throws SerialException {
		boolean verified = false;
		// Baud rate detect
		try {
			// 0x7F is 01111111 binary, which is the maximum useful # of bit toggles
			port.write(0x7F);
			port.flush();
			// An ACK here means we're good
			verify();
			verified = true;
		} catch (SerialException ignore) {
			// Verification errors are dumped here
		} catch (IOException e) {
			throw new SerialException("I/O error during negotiation", e);
		}
		return verified;
	}
	/**
	 * Sets the active STM device without querying. Useful if you only need one thing or if
	 * the device is always the same one.
	 *
	 * @param device the device parameters to use
	 */
	public void setDevice(final STMDevice device) {
		this.device = device;
	}
	/**
	 * Verifies correct serial transfer by checking the verification byte.
	 *
	 * @throws SerialException if an I/O error occurs, or if the controller does not properly
	 * acknowledge transmission with an ACK byte
	 */
	public void verify() throws SerialException {
		int result = -1;
		// EOF avoidance and retry
		for (int i = 0; i < RETRIES && result == -1; i++)
			try {
				result = Utils.readOne(port);
			} catch (IOException e) {
				result = -1;
			}
		if (result == -1)
			throw new SerialException("Connection lost to controller (EOF)");
		// Bad acknowledge
		if (result != 0x79)
			throw new SerialException(String.format("Controller non-acknowledge: %2X",
				(result & 0xFF)));
	}
}