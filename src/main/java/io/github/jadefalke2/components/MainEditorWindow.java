package io.github.jadefalke2.components;

import io.github.jadefalke2.Script;
import io.github.jadefalke2.TAS;
import io.github.jadefalke2.script.Format;
import io.github.jadefalke2.script.NXTas;
import io.github.jadefalke2.util.CorruptedScriptException;
import io.github.jadefalke2.util.Logger;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

public class MainEditorWindow extends JFrame {

	private final TAS parent;
	private final MainJMenuBar mainJMenuBar;

	//layout
	private final TabbedScriptsPane tabbedPane;
	private final JToolBar toolBar;

	private final JLabel scriptLengthLabel;


	/**
	 * Constructor
	 */
	public MainEditorWindow (TAS parent){
		this.parent = parent;

		Logger.log("Initialising window");

		setResizable(true);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); //let the WindowListener handle everything

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(parent.closeAllScripts()) {
					parent.exit();
				}
			}
		});

		mainJMenuBar = new MainJMenuBar(this);
		setJMenuBar(mainJMenuBar);

		toolBar = new JToolBar();
		scriptLengthLabel = new JLabel();
		setCurrentScriptLength(-1);
		toolBar.add(scriptLengthLabel);
		toolBar.setFloatable(false);
		toolBar.setMargin(new Insets(0, 5, 0, 0));
		add(toolBar, BorderLayout.PAGE_END);

		tabbedPane = new TabbedScriptsPane(this, this::setCurrentScriptLength);
		add(tabbedPane, BorderLayout.CENTER);

		pack(); //TODO is still too small, the joystick is too little to use
		setExtendedState(MAXIMIZED_BOTH);
	}


	public void openScript(File file, Format format) throws IOException {
		Logger.log("loading script from " + file.getAbsolutePath());
		// sets the current script file to be the one that the method is called with
		try {
			openScript(Format.read(file, format));
		} catch (CorruptedScriptException e) {
			e.printStackTrace();
		}
	}
	public void openScript(Script script){
		tabbedPane.openScript(script);
	}
	public void newFile(){
		Logger.log("opening a new, empty script");
		openScript(Script.getEmptyScript(10));
	}
	public boolean closeAllScripts() {
		return tabbedPane.closeAllScripts();
	}

	public void onUndoRedo(boolean enableUndo, boolean enableRedo) {
		getActiveScriptTab().updateSelectedRows();
		enableUndoRedo(enableUndo, enableRedo);
	}
	public void enableUndoRedo(boolean enableUndo, boolean enableRedo) {
		mainJMenuBar.updateUndoMenu(enableUndo, enableRedo);
	}

	public void saveFile() throws IOException {
		getActiveScriptTab().saveFile();
	}
	public void saveFileAs() throws IOException {
		getActiveScriptTab().saveFileAs();
	}
	public void saveFileCopy() throws IOException {
		getActiveScriptTab().saveFileCopy();
	}

	/**
	 * Sends the active script straight to a Pico 2 running the TAS
	 * serial-upload firmware, over USB - no exported file needed. Probing
	 * for the device and the upload itself both touch serial I/O, so both
	 * run off the EDT via SwingWorker; only the dialogs get shown on it.
	 */
	public void uploadToPico() {
		Script script = getActiveScriptTab().getScript();
		String movieText = NXTas.write(script);

		JDialog progress = new JDialog(this, "Uploading to Pico 2", false);
		JLabel progressLabel = new JLabel("Looking for Pico 2...", SwingConstants.CENTER);
		progressLabel.setBorder(BorderFactory.createEmptyBorder(24, 36, 24, 36));
		progress.add(progressLabel);
		progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		progress.setMinimumSize(new Dimension(300, 120));
		progress.pack();
		progress.setLocationRelativeTo(this);
		progress.setVisible(true);

		new SwingWorker<Void, Void>() {
			private String error = null;

			@Override
			protected Void doInBackground() {
				try {
					String port = PicoUploader.findDevicePortName();
					if (port == null) {
						error = "Couldn't find a Pico 2. Make sure it's plugged in and running the TAS firmware.";
						return null;
					}
					PicoUploader.upload(port, movieText);
				} catch (PicoUploader.UploadException e) {
					error = e.getMessage();
				}
				return null;
			}

			@Override
			protected void done() {
				progress.dispose();
				if (error != null) {
					JOptionPane.showMessageDialog(MainEditorWindow.this, error, "Upload failed", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(MainEditorWindow.this, "Uploaded! It's live on the Pico 2 now, no reset needed.", "Upload complete", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		}.execute();
	}
	public void addSingleEmptyRow() {
		getActiveScriptTab().getPianoRoll().addEmptyRows(1);
	}
	public void addMultipleEmptyRows() {
		ScriptTab tab = getActiveScriptTab();
		AddLinesDialog dialog = new AddLinesDialog(this, getActiveScriptTab().getPianoRoll().getSelectedRowCount());
		dialog.setVisible(true);
		if(dialog.isAccepted()) {
			int amount = dialog.getNumLines();
			tab.getPianoRoll().addEmptyRows(amount);
		}
	}

	public ScriptTab getActiveScriptTab() {
		return tabbedPane.getActiveScriptTab();
	}

	public void setAllTabsClosed(boolean closed) {
		mainJMenuBar.enableScriptRelatedInputs(!closed);
	}

	public void setCurrentScriptLength(int newLength) {
		scriptLengthLabel.setText("Length: "+newLength);
	}

	public void selectLines() {
		ScriptTab tab = getActiveScriptTab();
		SelectLinesDialog dialog = new SelectLinesDialog(this, tab.getScript().getLines().length);
		dialog.setVisible(true);
		if(dialog.isAccepted()) {
			int[] selectedLines = dialog.getSelectedLines();
			tab.setSelectedLines(selectedLines);
		}
	}
	public void openSettings() {
		Logger.log("opening settings");
		new SettingsDialog(this).setVisible(true);
	}
	public void newWindow() {
		parent.newWindow();
	}
}
