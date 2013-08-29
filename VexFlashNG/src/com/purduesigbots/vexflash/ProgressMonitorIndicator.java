package com.purduesigbots.vexflash;

import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Indicates flash progress to Eclipse.
 * 
 * @author Stephen
 */
public class ProgressMonitorIndicator implements Indicator {
	private int lastProgress = 0;
	private IProgressMonitor mon;

	public ProgressMonitorIndicator(final IProgressMonitor mon) {
		this.mon = mon;
	}
	public void begin() {
		lastProgress = 0;
		mon.subTask("Uploading code... 0%");
	}
	public void message(final String message) {
		mon.subTask(message);
	}
	public void messageBegin(final String message) {
		mon.subTask(message);
	}
	public void messageEnd(final String message) {
	}
	public void progress(final int progress) {
		mon.subTask("Uploading code... " + progress + "%");
		mon.worked(progress - lastProgress);
		lastProgress = progress;
	}
	public void end() {
	}
}
