package com.purduesigbots.vexflash;

import java.io.*;
import jssc.*;

/**
 * Wrapper class that hopes to maintain compatibility across JSSC and RXTX for serial.
 * 
 * @author Stephen
 */
public class SerialPortIO implements SerialPortEventListener {
	private final SerialPort port;
	private final Object rxLock;
	private long timeout;
	private final Object txLock;

	/**
	 * Opens a new serial port with the given name.
	 * 
	 * @param name the serial port name
	 * @throws IOException if an I/O error occurs
	 */
	public SerialPortIO(final String name) throws IOException {
		rxLock = new Object();
		port = new SerialPort(name);
		timeout = 0L;
		txLock = new Object();
		try {
			// Open port and set default parameters
			port.openPort();
			port.setDTR(false);
			port.setRTS(false);
			port.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			port.setEventsMask(SerialPort.MASK_RXCHAR | SerialPort.MASK_RXFLAG |
				SerialPort.MASK_TXEMPTY);
			port.addEventListener(this);
		} catch (SerialPortException e) {
			if (e.getExceptionType().equals(SerialPortException.TYPE_PORT_ALREADY_OPENED))
				throw new IOException("Port is in use");
			else
				throw new IOException("Failed to open port");
		}
	}
	/**
	 * Closes the serial port.
	 */
	public void close() {
		try {
			flush();
			// Clean it up
			port.closePort();
		} catch (SerialPortException ignore) { }
	}
	/**
	 * Flushes the serial port's output buffers.
	 */
	public void flush() {
		try {
			while (port.getOutputBufferBytesCount() > 0)
				synchronized (txLock) {
					txLock.wait(30);
				}
		} catch (Exception ignore) { }
	}
	/**
	 * Gets the name of the port.
	 * 
	 * @return the port's name
	 */
	public String getName() {
		return port.getPortName();
	}
	/**
	 * Gets the current timeout.
	 * 
	 * @return the port timeout, or 0 if none is set
	 */
	public long getTimeout() {
		return timeout;
	}
	/**
	 * Purges all bytes from the input buffer.
	 */
	public void purge() {
		try {
			port.purgePort(SerialPort.PURGE_RXCLEAR);
		} catch (SerialPortException ignore) { }
	}
	/**
	 * Reads one byte from the serial port.
	 * 
	 * @return the byte read
	 * @throws IOException if the data cannot be read, or a timeout occurs
	 */
	public byte read() throws IOException {
		return read(1)[0];
	}
	/**
	 * Reads data from the serial port.
	 * 
	 * @param length the number of bytes to read
	 * @return the data read
	 * @throws IOException if the data cannot be read, or a timeout occurs
	 */
	public byte[] read(final int length) throws IOException {
		byte[] val;
		try {
			synchronized (rxLock) {
				int count = port.getInputBufferBytesCount();
				// If ready, read it in
				if (count >= length)
					val = port.readBytes(length);
				else {
					// Wait it out until we either get what we want or error occurs
					final long future = System.currentTimeMillis() + timeout;
					long now = System.currentTimeMillis();
					do {
						try {
							rxLock.wait(future - now);
						} catch (InterruptedException ignore) { }
						// Got data, check to see if it is useful
						count = port.getInputBufferBytesCount();
						now = System.currentTimeMillis();
					} while (count < length && now < future);
					if (count >= length)
						// Got what we wanted
						val = port.readBytes(length);
					else
						throw new IOException("Timeout when reading " + length + " bytes");
				}
			}
		} catch (SerialPortException e) {
			throw new IOException("Error when reading " + length + " bytes");
		}
		return val;
	}
	public void serialEvent(final SerialPortEvent e) {
		if (e.isRXCHAR() || e.isRXFLAG())
			synchronized (rxLock) {
				// Notify everyone that there's data
				rxLock.notifyAll();
			}
		else if (e.isTXEMPTY())
			synchronized (txLock) {
				// Notify everyone that TX is done
				txLock.notifyAll();
			}
	}
	/**
	 * Sets the DTR control bit.
	 * 
	 * @param enabled whether DTR should be asserted or negated
	 * @throws IOException if an I/O error occurs
	 */
	public void setDTR(final boolean enabled) throws IOException {
		try {
			port.setDTR(enabled);
		} catch (SerialPortException e) {
			throw new IOException("Failed to set DTR = " + enabled);
		}
	}
	/**
	 * Sets the RTS control bit.
	 * 
	 * @param enabled whether RTS should be asserted or negated
	 * @throws IOException if an I/O error occurs
	 */
	public void setRTS(final boolean enabled) throws IOException {
		try {
			port.setRTS(enabled);
		} catch (SerialPortException e) {
			throw new IOException("Failed to set RTS = " + enabled);
		}
	}
	/**
	 * Sets the port parameters.
	 * 
	 * @param baud the baud rate to set
	 * @param parity the parity mode to use
	 * @throws IOException if an I/O error occurs
	 */
	public void setParams(final int baud, final int parity) throws IOException {
		try {
			port.setParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, parity,
				false, false);
		} catch (SerialPortException e) {
			throw new IOException("Failed to set port parameters");
		}
	}
	/**
	 * Changes the port timeout.
	 * 
	 * @param timeout the timeout in milliseconds for reading bytes, or 0 to disable it
	 */
	public void setTimeout(final long timeout) {
		this.timeout = timeout;
	}
	/**
	 * Writes data to the serial port.
	 * 
	 * @param data the byte to write; the 24 high order bits are ignored
	 * @throws IOException if an I/O error occurs
	 */
	public void write(final int data) throws IOException {
		try {
			port.writeInt(data);
		} catch (SerialPortException e) {
			throw new IOException("Error when writing byte");
		}
	}
	/**
	 * Writes data to the serial port.
	 * 
	 * @param data the byte array to write
	 * @throws IOException if an I/O error occurs
	 */
	public void write(final byte[] data) throws IOException {
		try {
			port.writeBytes(data);
		} catch (SerialPortException e) {
			throw new IOException("Error when writing " + data.length + " bytes");
		}
	}
	/**
	 * Writes data to the serial port.
	 * 
	 * @param data the string to write
	 * @throws IOException if an I/O error occurs
	 */
	public void write(final String str) throws IOException {
		try {
			port.writeString(str);
		} catch (SerialPortException e) {
			throw new IOException("Error when writing " + str.length() + " characters");
		}
	}
}