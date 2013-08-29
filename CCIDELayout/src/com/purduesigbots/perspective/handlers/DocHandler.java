package com.purduesigbots.perspective.handlers;

import org.eclipse.core.commands.*;
import org.eclipse.swt.program.*;

/**
 * Displays the online documentation for PROS.
 */
public class DocHandler extends AbstractHandler {
	public static final String DOC_URL = "http://purdueros.sourceforge.net/doc";

	public Object execute(ExecutionEvent event) throws ExecutionException {
		Program.launch(DOC_URL);
		return null;
	}
}
