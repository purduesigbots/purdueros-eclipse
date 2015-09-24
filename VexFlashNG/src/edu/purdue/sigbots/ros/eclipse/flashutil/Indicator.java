package edu.purdue.sigbots.ros.eclipse.flashutil;

/**
 * An interface describing a progress indicator for programming.
 * 
 * @author Stephen
 */
public interface Indicator {
	/**
	 * Indicate that flashing has begun.
	 */
	public void begin();
	/**
	 * Outputs a message to a user-visible location.
	 * 
	 * @param message the message to output
	 */
	public void message(final String message);
	/**
	 * Outputs a message to a user-visible location. This is meant for begin-of-process
	 * notifications; on the command line, no newline will occur after this message.
	 * 
	 * @param message the message to output
	 */
	public void messageBegin(final String message);
	/**
	 * Outputs a message to a user-visible location. This is meant for end-of-process
	 * notifications; these will not appear in Eclipse.
	 * 
	 * @param message the message to output
	 */
	public void messageEnd(final String message);
	/**
	 * Indicate that flashing has progressed.
	 * 
	 * @param message the current program status
	 * @param progress what percentage complete the flash job has reached
	 */
	public void progress(final int progress);
	/**
	 * Indicate that flashing has ended.
	 */
	public void end();
}
