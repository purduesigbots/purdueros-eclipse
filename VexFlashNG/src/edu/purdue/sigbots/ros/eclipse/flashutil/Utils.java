package edu.purdue.sigbots.ros.eclipse.flashutil;

import java.io.*;

/**
 * Utility class, mostly containing shared I/O routines.
 */
public final class Utils {
	/**
	 * Computes a STM32 checksum.
	 *
	 * @param current the current checksum value (use 0 to start from scratch)
	 * @param data the data bytes to check
	 * @param offset location in data to start computation
	 * @param length number of bytes to check
	 * @return the checksum for the specified portion of data
	 */
	public static byte checksum(byte current, final byte[] data, final int offset,
			final int length) {
		for (int i = offset; i < offset + length; i++)
			current ^= data[i] & 0xFF;
		return (byte)current;
	}
	/**
	 * Delays for the given time period.
	 *
	 * @param time the length of the delay in milliseconds
	 */
	public static void delay(final long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException ignore) { }
	}
	/**
	 * Wipes the serial input port buffers.
	 *
	 * @param port the port to clear
	 */
	public static void eat(final SerialPortIO port) {
		port.purge();
	}
	/**
	 * Executes the command, optionally copying output to standard output.
	 * 
	 * @param out whether the output should be shown on stdout
	 * @param command the command and arguments to run
	 * @return the exit code of the program
	 * @throws IOException if the program cannot be launched
	 */
	public static int execPrint(final boolean out, final String... command) throws IOException {
		final ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		final Process p = builder.start();
		final InputStream is = p.getInputStream();
		int c;
		// Read-print loop
		while ((c = is.read()) >= 0)
			if (out) {
				System.out.write(c);
				System.out.flush();
			}
		is.close();
		return p.exitValue();
	}
	/**
	 * Determines the bytecode for a specified memory address in big endian unsigned 32-bit
	 * format.
	 *
	 * @param address the address to encode
	 * @return the address as a byte array with checksum
	 */
	public static byte[] memAddress(final int address) {
		final byte[] data = new byte[] {
			(byte)((address >> 24) & 0xFF), (byte)((address >> 16) & 0xFF),
			(byte)((address >> 8) & 0xFF), (byte)(address & 0xFF), 0x00
		};
		data[4] = checksum((byte)0, data, 0, 4);
		return data;
	}
	/**
	 * Opens a serial port, given its identifier.
	 * 
	 * @param id the identifier to use
	 * @return the serial port object
	 * @throws SerialException if an I/O error occurs
	 */
	public static SerialPortIO openSerialPort(final String id) throws SerialException {
		try {
			return new SerialPortIO(id);
		} catch (IOException e) {
			throw new SerialException(e.getMessage(), e);
		}
	}
	/**
	 * Reads the specified number of bytes from an input stream.
	 *
	 * @param is the input stream to read
	 * @param length the number of bytes to read
	 * @return the data read
	 * @throws IOException if an I/O error occurs
	 */
	public static byte[] readExactly(final SerialPortIO port, final int length)
			throws IOException {
		return port.read(length);
	}
	/**
	 * Reads a single byte from an input stream.
	 *
	 * @param is the input stream to read
	 * @return the byte read (the 24 high-order bits are all zeroes)
	 * @throws IOException if an I/O error occurs, or EOF is reached
	 */
	public static int readOne(final SerialPortIO port) throws IOException {
		return port.read();
	}

	// Utility class...
	private Utils() { }
}