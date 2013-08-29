package com.purduesigbots.vexflash;

/**
 * Represents an STM device.
 */
public class STMDevice {
	/**
	 * Possible STM device codes to determine Flash and RAM areas.
	 * In case a new Vex Cortex comes out with another microcontroller, we are covered!
	 */
	public static final STMDevice[] STM_DEVICES = {
		new STMDevice(0x412, "Low-density", 0x20000200, 0x20002800, 0x08008000, 4,
			1024),
		new STMDevice(0x410, "Medium-density", 0x20000200, 0x20005000, 0x08020000,
			4, 1024),
		// Change for high-density line to limit codesize to 384 KB
		new STMDevice(0x414, "High-density", 0x20000200, 0x20010000, 0x08060000,
			2, 2048),
		new STMDevice(0x418, "Connectivity line", 0x20001000, 0x20010000,
			0x08040000, 2, 2048),
		new STMDevice(0x420, "Medium-density VL", 0x20000200, 0x20002000,
			0x08020000, 4, 1024),
		new STMDevice(0x430, "XL-density", 0x20000800, 0x20018000, 0x08100000, 2,
			2048)
	};

	/**
	 * Address of the end of Flash (unsigned!)
	 */
	private final int flashEnd;
	/**
	 * Address of the start of Flash (unsigned!)
	 */
	private final int flashStart;
	/**
	 * Device PID (16-bit)
	 */
	private final short id;
	/**
	 * The human-readable device name.
	 */
	private final String name;
	/**
	 * The number of pages per sector.
	 */
	private final int pageCount;
	/**
	 * The page size in bytes.
	 */
	private final int pageSize;
	/**
	 * Address of the end of RAM (unsigned!)
	 */
	private final int ramEnd;
	/**
	 * Address of the start of RAM (unsigned!)
	 */
	private final int ramStart;

	/**
	 * Constructor is called to create STM device prototype.
	 *
	 * @param id the device 16-bit ID returned when a GID is sent
	 * @param name the human-readable device class name
	 * @param ramStart 32-bit location where RAM starts
	 * @param ramEnd 32-bit location where RAM ends
	 * @param flashEnd 32-bit location where Flash ends (flash always starts at 0x08000000)
	 * @param pageCount number of Flash pages per sector
	 * @param pageSize size of a Flash page
	 */
	public STMDevice(final int id, final String name, final int ramStart, final int ramEnd,
			final int flashEnd, final int pageCount, final int pageSize) {
		this.flashEnd = flashEnd;
		flashStart = 0x08000000;
		this.id = (short)id;
		this.name = name;
		this.pageCount = pageCount;
		this.pageSize = pageSize;
		this.ramEnd = ramEnd;
		this.ramStart = ramStart;
	}
	/**
	 * Gets the ending address of Flash.
	 * 
	 * @return the Flash end address (unsigned!)
	 */
	public int getFlashEnd() {
		return flashEnd;
	}
	/**
	 * Gets the size of the Flash memory in bytes.
	 *
	 * @return the Flash size in bytes
	 */
	public int getFlashSize() {
		return getFlashEnd() - getFlashStart();
	}
	/**
	 * Gets the starting address of Flash. Typically where user code begins.
	 *
	 * @return the Flash start address (unsigned!)
	 */
	public int getFlashStart() {
		return flashStart;
	}
	/**
	 * Gets the PID for this device.
	 * 
	 * @return the PID returned by GID commands
	 */
	public short getID() {
		return id;
	}
	/**
	 * Gets the human-readable device type.
	 * 
	 * @return a string describing the type of STM32 device in use
	 */
	public String getName() {
		return name;
	}
	/**
	 * Gets the number of Flash pages per sector.
	 * 
	 * @return the number of pages per sector
	 */
	public int getPageCount() {
		return pageCount;
	}
	/**
	 * Gets the size of a Flash page.
	 * 
	 * @return the size of a Flash page in bytes
	 */
	public int getPageSize() {
		return pageSize;
	}
	/**
	 * Gets the ending address of RAM.
	 *
	 * @return the RAM end address (unsigned!)
	 */
	public int getRamEnd() {
		return ramEnd;
	}
	/**
	 * Gets the size of the SRAM in bytes.
	 *
	 * @return the RAM size in bytes
	 */
	public int getRAMSize() {
		return getRamEnd() - getRamStart();
	}
	/**
	 * Gets the starting address of RAM.
	 *
	 * @return the RAM start address (unsigned!)
	 */
	public int getRamStart() {
		return ramStart;
	}
}