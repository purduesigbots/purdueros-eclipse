package com.purduesigbots.vexflash;

import java.io.*;
import java.util.*;

/**
 * Modifies data on the PROS File System.
 */
public class FileSystemManipulator {
	/**
	 * Number of pages to erase in a block when doing multi page erase to increase upload
	 * reliability (erasing takes a long time!)
	 */
	private static final int ERASE_BLOCK = 16;
	/**
	 * Flag for the status field to indicate that a TRIM is required.
	 */
	public static final short FILE_FLAG_DEL = (short)0xA50A;
	/**
	 * Number of bytes in the file header.
	 */
	public static final int FILE_HEADER_SIZE = 16;
	/**
	 * Magic header for file system pages. This is an illegal ARM instruction, reducing the
	 * chance of identifying an oversized code page as a file. This is in LITTLE ENDIAN
	 * byte order.
	 */
	public static final short FILE_MAGIC = (short)0xDEDE;
	/**
	 * The number of characters of a file name that are preserved; anything else is cut off.
	 */
	public static final int FILE_NAME_LEN = 8;
	/**
	 * The maximum number of bytes that can go in one upload packet.
	 */
	public static final int R_SIZE = 32;
	/**
	 * The maximum number of bytes that can go in one download packet.
	 */
	public static final int W_SIZE = 256;

	/**
	 * The last valid page # for the filesystem. This is an offset from the VEX file system
	 * page start!
	 */
	private final int lastPage;
	/**
	 * The currently connected device
	 */
	private final STMState state;

	/**
	 * Creates a file system manipulator to modify the specified device.
	 * 
	 * @param state the currently connected device
	 */
	public FileSystemManipulator(final STMState state) {
		final STMDevice dev = state.getDevice();
		// This value is used everywhere!
		lastPage = (dev.getFlashEnd() - dev.getFlashStart() - VexFlash.FS_START) /
			dev.getPageSize();
		this.state = state;
	}
	/**
	 * Creates a file system preamble.
	 * 
	 * @param name the file name; only the first 8 characters will be used
	 * @param length the file size in bytes
	 * @return the preamble for this file
	 */
	public byte[] createPreamble(final String name, final int length) {
		final byte[] preamble = new byte[FILE_HEADER_SIZE];
		// Magic
		preamble[0] = (byte)(FILE_MAGIC & 0xFF);
		preamble[1] = (byte)(FILE_MAGIC >> 8);
		// Status = 0xFFFF
		preamble[2] = (byte)0xFF;
		preamble[3] = (byte)0xFF;
		// Length as little endian (unsigned) 32-bit integer
		preamble[4] = (byte)(length & 0xFF);
		preamble[5] = (byte)((length >> 8) & 0xFF);
		preamble[6] = (byte)((length >> 16) & 0xFF);
		preamble[7] = (byte)((length >> 24) & 0xFF);
		// Prefill name with null terminators
		for (int i = 0; i < FILE_NAME_LEN; i++)
			preamble[8 + i] = (byte)0;
		// File name
		final byte[] nameBytes = name.getBytes();
		System.arraycopy(nameBytes, 0, preamble, 8, Math.min(FILE_NAME_LEN, nameBytes.length));
		return preamble;
	}
	/**
	 * Downloads the file to the flash file system.
	 * 
	 * @param fileData the file to download
	 * @param output the indicator of progress
	 * @throws SerialException if an I/O error occurs while communicating
	 * @throws IOException if an I/O error occurs when reading the input file
	 */
	public void download(final String name, final Parser fileData, final Indicator output)
			throws SerialException, IOException {
		final BitSet bitmap = new BitSet(lastPage + 2);
		final STMDevice dev = state.getDevice();
		final int size = fileData.length(), ps = dev.getPageSize();
		// Set a bit at the end of the file system to denote EOS
		bitmap.set(0, lastPage + 1);
		// Try to erase the current copy of this file, if it exists
		final FileEntry entry = lookForFile(name, bitmap);
		if (entry != null && entry.start >= 0) {
			final int end = entry.start + entry.len;
			final int vexStart = VexFlash.FS_START / ps;
			eraseRange(vexStart + entry.start, vexStart + end - 1);
			// Pages are now available, clear the bitset
			bitmap.clear(entry.start, end);
		}
		// Look for room
		int end = 0;
		for (int start = bitmap.nextClearBit(0); start < lastPage;
				start = bitmap.nextClearBit(end)) {
			// Iterate over clear bits, find a range of clear bits large enough
			// nextClearBit cannot be negative, there is always another clear bit
			end = bitmap.nextSetBit(start);
			if (end >= 0 && end <= lastPage) {
				// Start and end are inside the set (end is exclusive remember!)
				if (size + FILE_HEADER_SIZE <= ps * (end - start)) {
					writeDataToAddress(VexFlash.FS_START + start * ps, createPreamble(name,
						size), fileData, output);
					return;
				}
			} else
				// There is not space in the file system
				break;
		}
		throw new SerialException(String.format("File system lacks available space for " +
			"this file (%d KiB)", size / 1024));
	}
	/**
	 * Erases a range of flash memory pages.
	 * 
	 * @param start the starting page to erase (inclusive)
	 * @param end the ending page to erase (inclusive)
	 */
	public void eraseRange(final int start, final int end) throws SerialException {
		final SerialPortIO port = state.getPort();
		// Populate with 0...15 and erase, then 16...31 and erase, ...
		// This reduces the time per command to increase the reliability of the serial link		
		port.setTimeout(2000L);
		for (int page = start; page <= end; ) {
			final int count = Math.min(ERASE_BLOCK, end - page + 1);
			// Write appropriate # of pages
			final byte[] which = new byte[count];
			for (int i = 0; i < count; i++)
				which[i] = (byte)(page++ & 0xFF);
			state.commandER(which);
		}
		// Reset timeout to default
		port.setTimeout(VexFlash.VEX_TIMEOUT);
	}
	/**
	 * Looks for a file with the given file name. The match is case sensitive.
	 * 
	 * @param name the file name to search for
	 * @param bitmap a bit set for page status for each page in the file system, or null if
	 * this information is not useful
	 * @return the address of the file, or null if the file does not exist
	 * @throws SerialException if an I/O error occurs while communicating
	 */
	private FileEntry lookForFile(final String name, final BitSet bitmap)
			throws SerialException {
		FileEntry entry = new FileEntry(), rv = null;
		final String newName;
		// Truncate file name
		if (name.length() > FILE_NAME_LEN)
			newName = name.substring(0, FILE_NAME_LEN);
		else
			newName = name;
		// The default entry has count == 0 but len == 1 since the header takes a "page"
		for (; (entry = nextValidFile(entry.start + entry.len, bitmap)) != null; ) {
			if (newName.equals(entry.name) && rv == null) {
				rv = entry;
				// Prevent early termination if the bitmap must be populated
				if (bitmap == null) break;
			}
		}
		return rv;
	}
	/**
	 * Returns the index of the first valid file entry (i.e. not TRIM, no-use, etc.) starting
	 * at index. If the bitmap parameter is provided, computes a free space bitmap of the pages
	 * traversed in this way.
	 * 
	 * @param index the page index to start looking
	 * @param bitmap a bit set for page status for each page in the file system, or null if
	 * this information is not useful (a set bit is a <b>full</b> page)
	 * @return the first valid file entry page greater than or equal to index, or null if no
	 * such page exists
	 * @throws SerialException if an I/O error occurs while communicating
	 */
	private FileEntry nextValidFile(final int index, final BitSet bitmap)
			throws SerialException {
		final STMDevice dev = state.getDevice();
		final int ps = dev.getPageSize();
		// Read data from pages until we get a valid magic
		for (int i = index; i < lastPage; i++) {
			final byte[] data = readDataFromAddress(i * ps + VexFlash.FS_START,
				FILE_HEADER_SIZE, null);
			if (data[2] == (byte)0xFF && data[3] == (byte)0xFF) {
				// File is not erased
				if (bitmap != null) {
					if (data[0] == (byte)0xFF && data[1] == (byte)0xFF)
						// Page is free
						bitmap.clear(i);
					else
						// Page is used
						bitmap.set(i);
				}
				if (data[0] == (byte)(FILE_MAGIC & 0xFF) && data[1] == (byte)(FILE_MAGIC >> 8)) {
					// Magic OK, status = 0xFFFF (not TRIM)
					final int len = (data[4] & 0xFF) | ((data[5] & 0xFF) << 8) |
						((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 24);
					String name = new String(data, 8, FILE_NAME_LEN);
					final int idx = name.indexOf('\0');
					// Trim "\0" from the end of name
					if (idx > 0)
						name = name.substring(0, idx);
					final FileEntry fe = new FileEntry(i, len, name, ps);
					if (len < dev.getFlashSize())
						// Length is sane, calculate the # of pages this file consumes
						return fe;
					i += fe.len;
				}
			}
		}
		return null;
	}
	/**
	 * Reads data from an address in Flash memory.
	 * 
	 * @param start the offset to start reading in bytes from state.getUserCodeAddress()
	 * @param len the amount of data to read in bytes
	 * @param output the indicator of progress
	 * @return the data thus read
	 * @throws SerialException if an I/O error occurs while communicating
	 */
	public byte[] readDataFromAddress(final int start, final int size, final Indicator output)
			throws SerialException {
		int offset = 0, len;
		// Get start address
		final int addr = state.getUserCodeAddress();
		final byte[] ret = new byte[size];
		if (output != null)
			output.begin();
		try {
			// Read data from memory
			while (offset < size) {
				byte[] buffer;
				// Send read command
				len = Math.min(R_SIZE, size - offset);
				try {
					buffer = state.commandRD(addr + offset + start, len);
					Utils.delay(20);
				} catch (SerialException e) {
					// Wait 1.5s for reconnect
					Utils.delay(1500);
					// Flush buffers
					Utils.eat(state.getPort());
					// If we got some stuff OK, then restart flashing from this address
					buffer = state.commandRD(addr + offset + start, len);
				}
				System.arraycopy(buffer, 0, ret, offset, len);
				// Compute progress
				offset += len;
				if (output != null)
					output.progress(100 * offset / size);
			}
			return ret;
		} catch (SerialException e) {
			// Programming error!
			throw new SerialException("Connection lost to VEX device while uploading.\n" +
				"If this error frequently recurs, try another set of VEXnet keys, " +
				"or use the USB tether cable.", e);
		} finally {
			if (output != null)
				output.end();
		}
	}
	/**
	 * Uploads a file from the PROS File System, copying it to the given output directory.
	 * 
	 * @param name the file name to upload
	 * @param outputFolder the output folder to place a copy of the file
	 * @param output the indicator of progress
	 * @throws SerialException if an I/O error occurs while communicating
	 * @throws IOException if an I/O error occurs when writing the output file, or if the given
	 * file cannot be found on the VEX device
	 */
	public void upload(final String name, final File outputFolder, final Indicator output)
			throws IOException, SerialException {
		final FileEntry loc = lookForFile(name, null);
		if (loc != null)
			uploadFile(loc, new File(outputFolder, name), output);
		else
			// No such file
			throw new FileNotFoundException("On PROS FS: " + name);
	}
	/**
	 * Uploads a file from the PROS File System, copying it to the given output directory.
	 * 
	 * @param entry the file entry to upload
	 * @param target the output file on the local file system
	 * @param output the indicator of progress
	 * @throws SerialException if an I/O error occurs while communicating
	 * @throws IOException if an I/O error occurs when writing the output file
	 */
	private void uploadFile(final FileEntry entry, final File target, final Indicator output)
			throws IOException, SerialException {
		// Read the actual file data
		final String name = entry.name;
		final byte[] data = readDataFromAddress(VexFlash.FS_START + entry.start *
			state.getDevice().getPageSize(), FILE_HEADER_SIZE + entry.count, output);
		if (data != null) {
			// Open output file
			final OutputStream os = new BufferedOutputStream(new FileOutputStream(target));
			// Write it
			os.write(data, FILE_HEADER_SIZE, entry.count);
			os.close();
		} else
			// Failed to upload the file
			throw new SerialException("PROS FS error: " + name);
	}
	/**
	 * Uploads all files from the PROS File System, copying them to the given output directory.
	 * 
	 * @param outputFolder the output folder to place the copied files
	 * @param output the indicator of progress
	 * @throws SerialException if an I/O error occurs while communicating
	 * @throws IOException if an I/O error occurs when writing the output file
	 */
	public void uploadAllFiles(final File outputFolder, final Indicator output)
			throws IOException, SerialException {
		// Start bulk upload
		output.begin();
		try {
			FileEntry entry = new FileEntry();
			// The default entry has count == 0 but len == 1 since the header takes a "page"
			for (; (entry = nextValidFile(entry.start + entry.len, null)) != null; ) {
				output.message(entry.name);
				uploadFile(entry, new File(outputFolder, entry.name), null);
				output.progress(100 * entry.start / lastPage);
			}
		} finally {
			output.end();
		}
	}
	/**
	 * Writes data to an address in Flash memory, pre-assuming that those pages are empty.
	 * 
	 * @param start the offset to start writing in bytes from state.getUserCodeAddress()
	 * @param preamble the bytes to prepend to the file data; must be fewer than W_SIZE bytes
	 * @param fileData the data to write
	 * @param output the indicator of progress
	 * @throws SerialException if an I/O error occurs while communicating
	 * @throws IOException if an I/O error occurs when reading the input data
	 */
	public void writeDataToAddress(final int start, final byte[] preamble, final Parser fileData,
			final Indicator output) throws IOException, SerialException {
		int prelen = (preamble == null) ? 0 : preamble.length, len, offset = 0;
		// Get start address
		final int addr = state.getUserCodeAddress(), size = fileData.length() + prelen,
			flashSize = state.getFlashSize();
		final byte[] buffer = new byte[W_SIZE];
		// Too big?
		if (size >= flashSize)
			throw new SerialException(String.format("Data is too big to fit in memory.\n" +
				"File is %d KiB out of %d KiB", size / 1024, flashSize / 1024));
		output.begin();
		try {
			// Load preamble
			if (preamble != null && prelen > 0)
				System.arraycopy(preamble, 0, buffer, 0, prelen);
			// Write data to memory
			while (offset < size && (len = prelen + fileData.read(buffer, prelen,
					W_SIZE - prelen)) > 0) {
				// Fill buffer with alignment padding
				for (int i = len; i < buffer.length; i++)
					buffer[i] = (byte)0xFF;
				// Send write command
				try {
					state.commandWM(addr + offset + start, buffer);
					Utils.delay(20);
				} catch (SerialException e) {
					// Wait 1.5s for reconnect
					Utils.delay(1500);
					// Flush buffers
					Utils.eat(state.getPort());
					// If we got some stuff OK, then restart flashing from this address
					state.commandWM(addr + offset + start, buffer);
				}
				// Compute progress
				offset += len;
				output.progress(100 * offset / size);
				// Reset preamble
				prelen = 0;
			}
		} catch (SerialException e) {
			// Programming error!
			throw new SerialException("Connection lost to VEX device while uploading.\n" +
				"If this error frequently recurs, try another set of VEXnet keys, " +
				"or use the USB tether cable.", e);
		} finally {
			output.end();
		}
	}

	/**
	 * Denotes a file system entry found; it could be valid file or a blank space depending
	 * on context.
	 */
	protected static class FileEntry {
		/**
		 * The number of bytes in this file or space.
		 */
		protected final int count;
		/**
		 * The name of this file, or null for a space.
		 */
		protected final String name;
		/**
		 * The start page of this file or space (page, not bytes!)
		 */
		protected final int start;
		/**
		 * The number of pages consumed by this file or space.
		 */
		protected final int len;

		/**
		 * Creates a "start of file system" dummy entry.
		 */
		protected FileEntry() {
			this(-1, 0, null, 1024);
		}
		/**
		 * Creates a file system entry and calculates its page length.
		 * 
		 * @param start the entry start page (not the byte offset)
		 * @param count the number of bytes used excluding the file header
		 * @param name the file name, 8 characters max
		 * @param ps the page size of the device, used to calculate the page count
		 * @param space optional free space flag, set to -1 if not used
		 */
		protected FileEntry(final int start, final int count, final String name, final int ps) {
			this.count = count;
			this.len = (count + ps + (FILE_HEADER_SIZE - 1)) / ps;
			this.name = name;
			this.start = start;
		}
		public String toString() {
			return String.format("%s[start=%d,len=%d,name=%s]", getClass().getSimpleName(),
				start, len, name);
		}
	}
}