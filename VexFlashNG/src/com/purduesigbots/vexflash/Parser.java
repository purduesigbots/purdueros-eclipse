package com.purduesigbots.vexflash;

import java.io.*;

/**
 * Abstract superinterface of all parsers.
 */
public interface Parser {
	/**
	 * Closes the parser.
	 */
	public void close();
	/**
	 * Returns the size (in bytes) of the parser's data.
	 *
	 * @return the length of the user code
	 */
	public int length();
	/**
	 * Reads data from the parser.
	 *
	 * @param data location where the data read will be stored
	 * @param length maximum number of bytes to read
	 * @return the number of bytes actually read
	 * @throws IOException if a system I/O error occurs
	 */
	public int read(byte[] data, int length) throws IOException;
}
