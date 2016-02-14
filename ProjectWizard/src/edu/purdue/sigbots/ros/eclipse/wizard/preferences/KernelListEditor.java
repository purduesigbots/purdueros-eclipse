package edu.purdue.sigbots.ros.eclipse.wizard.preferences;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;

import edu.purdue.sigbots.ros.cli.management.KernelAvailabilityFlag;
import edu.purdue.sigbots.ros.cli.management.PROSActions;
import edu.purdue.sigbots.ros.eclipse.wizard.Activator;

public class KernelListEditor extends FieldEditor {
	private static final int LATEST_LOCAL_FONT_STYLE = SWT.ITALIC;
	private static final int LATEST_UPDATE_FONT_STYLE = SWT.BOLD;

	private Composite composite;
	private TableViewer tableViewer;
	private Button downloadButton;
	private Button deleteButton;

	public KernelListEditor(String labelText, Composite parent) {
		super("", labelText, parent);

		cliOut = new PrintStream(findConsole("PROS CLI").newMessageStream());
		cliErr = new PrintStream(findConsole("PROS CLI").newMessageStream());
	}

	public KernelListEditor(String name, String labelText, Composite parent) {
		super(name, labelText, parent);
		downloadButton.setText("Download");
		deleteButton.setText("Delete");

		cliOut = new PrintStream(findConsole("PROS CLI").newMessageStream());
		cliErr = new PrintStream(findConsole("PROS CLI").newMessageStream());
	}

	@Override
	protected void adjustForNumColumns(int numColumns) {
		((GridData) composite.getLayoutData()).horizontalSpan = numColumns;

	}

	@Override
	protected void doFillIntoGrid(Composite parent, int numColumns) {
		composite = new Composite(parent, SWT.NONE);

		GridLayout gridLayout = new GridLayout(5, true);
		composite.setLayout(gridLayout);

		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = numColumns;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		composite.setLayoutData(gridData);

		// Control label
		getLabelControl(composite).setText(getLabelText());
		getLabelControl(composite).setAlignment(SWT.CENTER);
		GridData labelData = new GridData(SWT.FILL, SWT.FILL, true, false);
		labelData.horizontalSpan = gridLayout.numColumns;
		getLabelControl(composite).setLayoutData(labelData);

		// Table
		Composite tableComposite = new Composite(composite, SWT.NONE);
		tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, gridLayout.numColumns - 1, 4));
		TableColumnLayout tableColumnLayout = new TableColumnLayout();
		tableComposite.setLayout(tableColumnLayout);
		tableViewer = new TableViewer(tableComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.setContentProvider(new ArrayContentProvider());
		tableViewer.addPostSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				Entry<String, Integer> selection = (Entry<String, Integer>) (((StructuredSelection) event
						.getSelection()).getFirstElement());
				if (selection != null) {
					downloadButton.setEnabled(KernelAvailabilityFlag.getFlags(selection.getValue())
							.contains(KernelAvailabilityFlag.KERNEL_AVAILABLE_ONLINE));
					deleteButton.setEnabled(KernelAvailabilityFlag.getFlags(selection.getValue())
							.contains(KernelAvailabilityFlag.KERNEL_AVAILABLE_LOCAL));
				} else {
					downloadButton.setEnabled(false);
					deleteButton.setEnabled(false);
				}
			}
		});

		// Kernel Column
		TableViewerColumn kernelColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		kernelColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Font getFont(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;
				Font font = JFaceResources.getDefaultFont();
				FontData fontData = font.getFontData()[0];
				int fontStyle = fontData.getStyle();
				try {
					PROSActions actions = Activator.getPROSActions();
					if (actions.resolveKernelLocalRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_LOCAL_FONT_STYLE;
					if (actions.resolveKernelUpdateRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_UPDATE_FONT_STYLE;
				} catch (IOException ignored) {
				}
				return new Font(font.getDevice(), new FontData(fontData.getName(), fontData.getHeight(), fontStyle));
			}

			public String getText(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;
				return entry.getKey();
			}
		});
		kernelColumn.getColumn().setText("Kernel");
		kernelColumn.getColumn().setAlignment(SWT.CENTER);
		tableColumnLayout.setColumnData(kernelColumn.getColumn(), new ColumnWeightData(20, false));

		// Online Column
		TableViewerColumn onlineColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		onlineColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Font getFont(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;
				Font font = JFaceResources.getDefaultFont();
				FontData fontData = font.getFontData()[0];
				int fontStyle = fontData.getStyle();
				try {
					PROSActions actions = Activator.getPROSActions();
					if (actions.resolveKernelLocalRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_LOCAL_FONT_STYLE;
					if (actions.resolveKernelUpdateRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_UPDATE_FONT_STYLE;
				} catch (IOException ignored) {
				}
				return new Font(font.getDevice(), new FontData(fontData.getName(), fontData.getHeight(), fontStyle));
			}

			public String getText(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;
				return KernelAvailabilityFlag.getFlags(entry.getValue())
						.contains(KernelAvailabilityFlag.KERNEL_AVAILABLE_ONLINE) ? "yes" : "no";
			}
		});
		onlineColumn.getColumn().setText("Online");
		onlineColumn.getColumn().setAlignment(SWT.CENTER);
		tableColumnLayout.setColumnData(onlineColumn.getColumn(), new ColumnWeightData(10, false));

		// Local Column
		TableViewerColumn localColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		localColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Font getFont(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;
				Font font = JFaceResources.getDefaultFont();
				FontData fontData = font.getFontData()[0];
				int fontStyle = fontData.getStyle();
				try {
					PROSActions actions = Activator.getPROSActions();
					if (actions.resolveKernelLocalRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_LOCAL_FONT_STYLE;
					if (actions.resolveKernelUpdateRequest("latest").contains(entry.getKey()))
						fontStyle |= LATEST_UPDATE_FONT_STYLE;
				} catch (IOException ignored) {

				}
				return new Font(font.getDevice(), new FontData(fontData.getName(), fontData.getHeight(), fontStyle));
			}

			@Override
			public String getText(Object element) {
				Entry<String, Integer> entry = (Entry<String, Integer>) element;

				return KernelAvailabilityFlag.getFlags(entry.getValue())
						.contains(KernelAvailabilityFlag.KERNEL_AVAILABLE_LOCAL) ? "yes" : "no";
			}
		});
		localColumn.getColumn().setText("Local");
		localColumn.getColumn().setAlignment(SWT.CENTER);
		tableColumnLayout.setColumnData(localColumn.getColumn(), new ColumnWeightData(10, false));

		// Download button
		downloadButton = new Button(composite, SWT.PUSH);
		GridData downloadButtonData = new GridData(SWT.FILL, SWT.DEFAULT, false, false, 1, 1);
		downloadButton.setLayoutData(downloadButtonData);
		downloadButton.setText("Download");
		downloadButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.type == SWT.Selection) {
					Entry<String, Integer> selection = (Entry<String, Integer>) (((StructuredSelection) tableViewer
							.getSelection()).getFirstElement());
					try {
						PROSActions actions = Activator.getPROSActions();
						actions.downloadKernel(selection.getKey());
					} catch (IOException e) {
						showErrorDialog("Error Downloading Kernel",
								"There was an error downloading the requested kernel", e);
					} finally {
						doLoad();
					}
				}
			}
		});

		// delete button
		deleteButton = new Button(composite, SWT.PUSH);
		GridData deleteButtonData = new GridData(SWT.FILL, SWT.DEFAULT, false, false, 1, 1);
		deleteButton.setLayoutData(deleteButtonData);
		deleteButton.setText("Delete");
		deleteButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (event.type == SWT.Selection) {
					Entry<String, Integer> selection = (Entry<String, Integer>) (((StructuredSelection) tableViewer
							.getSelection()).getFirstElement());
					try {
						PROSActions actions = Activator.getPROSActions();
						actions.deleteKernel(selection.getKey());
					} catch (IOException e) {
						showErrorDialog("Error Deleting Kernel", "There was an error deleting the requested kernel", e);
					} finally {
						doLoad();
					}
				}
			}
		});
		
		Button refreshButton = new Button(composite, SWT.PUSH);
		GridData refreshButtonData = new GridData(SWT.FILL, SWT.DEFAULT, false, false, 1, 1);
		refreshButton.setLayoutData(refreshButtonData);
		refreshButton.setText("Refresh");
		refreshButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if(event.type == SWT.Selection) {
					doLoad();
				}
			}
		});

		// Latest Local Kernel Identifier Label
		Label latestLocalLabel = new Label(composite, SWT.CENTER);
		FontData latestLocalLabelFontData = latestLocalLabel.getFont().getFontData()[0];
		latestLocalLabel.setFont(new Font(latestLocalLabel.getFont().getDevice(),
				new FontData(latestLocalLabelFontData.getName(), latestLocalLabelFontData.getHeight(),
						latestLocalLabelFontData.getStyle() | LATEST_LOCAL_FONT_STYLE)));
		latestLocalLabel.setText("latest local");
		latestLocalLabel.setToolTipText("A kernel will be italicized if it is the latest kernel you have downloaded.");
		latestLocalLabel.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false, 2, 1));

		// Latest Update Site Kernel Identifier Label
		Label latestUpdateLabel = new Label(composite, SWT.CENTER);
		FontData latestUpdateLabelFontData = latestUpdateLabel.getFont().getFontData()[0];
		latestUpdateLabel.setFont(new Font(latestUpdateLabel.getFont().getDevice(),
				new FontData(latestUpdateLabelFontData.getName(), latestUpdateLabelFontData.getHeight(),
						latestUpdateLabelFontData.getStyle() | LATEST_UPDATE_FONT_STYLE)));
		latestUpdateLabel.setText("latest update");
		latestUpdateLabel.setToolTipText(
				"A kernel will be bold if it is tagged as the latest kernel available on the update site.");
		latestUpdateLabel.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false, 2, 1));
	}

	@Override
	protected void doLoad() {
		try {
			PROSActions actions = Activator.getPROSActions();
			actions.getAllKernels();
			tableViewer.setInput(actions.getAllKernelsForce().entrySet());
		} catch(UnknownHostException e) {
			showErrorDialog("Error fetching all kernels", "There was an issue resolving the update site. Results may only include local kernels.", e);
		} catch (IOException e) {
			showErrorDialog("Error fetching all kernels", "There was an exception fetching the kernels", e);
		}
	}

	@Override
	protected void doLoadDefault() {
		doLoad();

	}

	@Override
	protected void doStore() {

	}

	@Override
	public int getNumberOfControls() {
		return 1;
	}

	private void showErrorDialog(String title, String message, Throwable t) {
		showErrorDialog(title, message, IStatus.ERROR, t);
	}

	private void showErrorDialog(String title, String message, int status, Throwable t) {
		StringWriter stringWriter = new StringWriter();
		PrintWriter traceWriter = new PrintWriter(stringWriter);
		t.printStackTrace(traceWriter);
		final String trace = stringWriter.toString();

		List<Status> childStatuses = new ArrayList<>();
		for (String line : trace.split(System.getProperty("line.separator"))) {
			childStatuses.add(new Status(status, Activator.PLUGIN_ID, line));
		}

		MultiStatus multiStatus = new MultiStatus(Activator.PLUGIN_ID, status,
				childStatuses.stream().toArray(s -> new Status[s]), t.getLocalizedMessage(), t);

		ErrorDialog.openError(composite.getShell(), title, message, multiStatus);
	}

	private MessageConsole findConsole(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager consoleManager = plugin.getConsoleManager();
		for (IConsole console : consoleManager.getConsoles()) {
			if (name.equals(console.getName())) {
				return (MessageConsole) console;
			}
		}
		MessageConsole console = new MessageConsole(name, null);
		consoleManager.addConsoles(new IConsole[] { console });
		return console;
	}

	private final PrintStream cliOut;
	private final PrintStream cliErr;
}
