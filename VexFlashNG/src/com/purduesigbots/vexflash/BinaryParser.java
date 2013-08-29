package com.purduesigbots.vexflash;

import java.io.*;

/**
 * A simple binary parser for BIN files.
 */
public class BinaryParser implements Parser {
	/**
	 * Input stream where data will be read.
	 */
	private final InputStream is;
	/**
	 * Cached length of the binary file, determined by constructor.
	 */
	private final int len;

	/**
	 * Creates a new binary parser.
	 *
	 * @param file the file to parse
	 * @throws IOException if an I/O error occurs
	 */
	public BinaryParser(final File file) throws IOException {
		is = new BufferedInputStream(new FileInputStream(file), 1024);
		len = (int)file.length();
	}
	public void close() {
		try {
			is.close();
		} catch (IOException ignore) { }
	}
	public int length() {
		return len;
	}
	public int read(final byte[] output, final int length) throws IOException {
		int offset = 0, read;
		// Can't use readExactly here because the signature is slightly different
		while (offset < length && (read = is.read(output, offset, length - offset)) > 0)
			offset += read;
		return offset;
	}
}