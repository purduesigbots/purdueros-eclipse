package com.purduesigbots.vexflash;

import java.util.*;

/**
 * Represents an abstract flash utility.
 */
public interface FlashUtility {
	/**
	 * Ends the flash process and closes the port(s). Always called, even if an error occurs
	 * in program().
	 */
	public void end();
	/**
	 * Gets the extension, without the ".", of the file type expected for this flash utility.
	 * 
	 * @return the expected file extension
	 */
	public String getExtension();
	/**
	 * Locates serial ports acceptable to this implementation.
	 * 
	 * @return a list of compatible serial ports
	 */
	public List<PortFinder.Serial> locateSerial();
	/**
	 * Programs the flash memory. If available, the indicator passed in can be used to show
	 * status.
	 * 
	 * @param output the indicator used to show progress
	 * @throws SerialException
	 */
	public void program(Indicator output) throws SerialException;
	/**
	 * Whether a valid serial port selection is required. MapleFlash can go without.
	 * 
	 * @return whether a serial port must be found before calling setup()
	 */
	public boolean requiresSerial();
	/**
	 * Sets up the flash program and opens ports if ready.
	 * 
	 * @param params the upload parameters to use
	 * @throws SerialException if an I/O error occurs
	 * @return true if and only if arguments were OK and flasher is ready to go (program())
	 */
	public boolean setup(UploadParams params) throws SerialException;
}