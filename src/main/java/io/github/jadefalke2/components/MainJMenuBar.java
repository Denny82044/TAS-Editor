package io.github.jadefalke2.components;

import io.github.jadefalke2.Script;
import io.github.jadefalke2.BuildInfo;
import io.github.jadefalke2.script.Format;
import io.github.jadefalke2.util.ObservableProperty;
import io.github.jadefalke2.util.Settings;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

public class MainJMenuBar extends JMenuBar {

	private static final int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

	private JMenuItem newScript, newWindow, openScript, save, saveAs, saveCopy, uploadToPico, exit;
	private JMenuItem undo, redo, cut, copy, paste, replace, deleteLines, selectLines, addLine, addLines, settings;
	private JCheckBoxMenuItem darkTheme;

	// ---- Pico 2 playback options: menu text (edit these to change what the menu says) ----
	private static final int[] HZ_PRESETS = {60, 50}; // shown in this order, above "Custom..."
	private static final String TEXT_LOOP = "Loop script";
	private static final String TIP_LOOP = "Restart the script from the beginning when it ends on the Pico 2 (saved in the script as \"LOOP\")";
	private static final String TEXT_DISCONNECT = "Disconnect controller on pause";
	private static final String TIP_DISCONNECT = "Disconnect the virtual controller from the Switch when pausing playback (saved in the script as \"DISCONNECT\")";
	private static final String TEXT_HZ_MENU = "Playback Hz: "; // current rate gets appended
	private static final String TIP_HZ_MENU = "How many frames per second the Pico 2 plays the script at (saved in the script as \"HZ\")";
	private static final String TEXT_HZ_UNIT = "Hz"; // preset items read "60Hz", "50Hz"
	private static final String TEXT_HZ_CUSTOM = "Custom...";
	private static final String TEXT_HZ_CUSTOM_ACTIVE_PREFIX = "Custom ("; // shown while a non-preset rate is set, e.g. "Custom (75Hz)..."
	private static final String TEXT_HZ_CUSTOM_ACTIVE_SUFFIX = ")...";
	private static final String TITLE_HZ_DIALOG = "Custom playback Hz";
	private static final String TEXT_HZ_DIALOG = "Playback rate in Hz (%d - %d):";
	private static final String TITLE_HZ_ERROR = "Invalid Hz";
	private static final String TEXT_HZ_ERROR = "Please enter a whole number between %d and %d.";

	private JCheckBoxMenuItem loopScript;
	private JCheckBoxMenuItem disconnectOnPause;
	private JMenu hzMenu;
	private JRadioButtonMenuItem[] hzPresetItems;
	private JRadioButtonMenuItem hzCustomItem;
	private final MainEditorWindow mainEditorWindow;

	public MainJMenuBar(MainEditorWindow mainEditorWindow){
		this.mainEditorWindow = mainEditorWindow;
		JMenu fileMenu = createFileMenu(mainEditorWindow);
		add(fileMenu);

		JMenu editMenu = createEditMenu(mainEditorWindow);
		add(editMenu);

		JMenu viewMenu = createViewMenu();
		add(viewMenu);

		JMenu aboutMenu = createAboutMenu();
		add(aboutMenu);

		updateUndoMenu(false, false);
	}

	private JMenu createFileMenu(MainEditorWindow mainEditorWindow){
		JMenu fileJMenu = new JMenu("File");

		newScript = fileJMenu.add("New");
		newScript.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut));
		newScript.addActionListener(e -> mainEditorWindow.newFile());

		newWindow = fileJMenu.add("New Window");
		newWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut | InputEvent.SHIFT_DOWN_MASK));
		newWindow.addActionListener(e -> mainEditorWindow.newWindow());

		openScript = fileJMenu.add("Open...");
		openScript.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut));
		openScript.addActionListener(e -> {
			TasFileChooser chooser = new TasFileChooser(Settings.INSTANCE.directory.get());
			File selectedFile = chooser.getFile(true);
			Format format = chooser.getFormat();
			if(selectedFile == null) return;

			try {
				mainEditorWindow.openScript(selectedFile, format);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});

		save = fileJMenu.add("Save");
		save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut));
		save.addActionListener(e -> {
			try {
				mainEditorWindow.saveFile();
			} catch(IOException ioe) {
				JOptionPane.showMessageDialog(null, "Failed to save file!\nError: "+ioe.getMessage(), "Saving failed", JOptionPane.ERROR_MESSAGE);
			}
		});

		saveAs = fileJMenu.add("Save As...");
		saveAs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut | InputEvent.SHIFT_DOWN_MASK));
		saveAs.addActionListener(e -> {
			try {
				mainEditorWindow.saveFileAs();
			} catch(IOException ioe) {
				JOptionPane.showMessageDialog(null, "Failed to save file!\nError: "+ioe.getMessage(), "Saving failed", JOptionPane.ERROR_MESSAGE);
			}
		});

		saveCopy = fileJMenu.add("Save Copy to...");
		saveCopy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut | InputEvent.ALT_DOWN_MASK));
		saveCopy.addActionListener(e -> {
			try {
				mainEditorWindow.saveFileCopy();
			} catch(IOException ioe) {
				JOptionPane.showMessageDialog(null, "Failed to save file!\nError: "+ioe.getMessage(), "Saving failed", JOptionPane.ERROR_MESSAGE);
			}
		});
		saveCopy.setToolTipText("Save current state into other file, but keep the current file opened so future saves will still go into the first location");

		fileJMenu.addSeparator();

		uploadToPico = fileJMenu.add("Upload to Pico 2...");
		uploadToPico.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, shortcut));
		uploadToPico.addActionListener(e -> mainEditorWindow.uploadToPico());
		uploadToPico.setToolTipText("Send the current script straight to a Pico 2 running the TAS firmware over USB");

		addPlaybackOptions(fileJMenu);

		fileJMenu.addSeparator();

		exit = fileJMenu.add("Exit");
		exit.addActionListener(e -> mainEditorWindow.dispatchEvent(new WindowEvent(mainEditorWindow, WindowEvent.WINDOW_CLOSING)));

		return fileJMenu;
	}

	/**
	 * Adds the per-script Pico 2 playback options below "Upload to Pico 2...". They're stored in the script itself,
	 * so they get written to the "LOOP" / "HZ" / "DISCONNECT" header lines when saving and uploading. Their state is
	 * refreshed from the active script whenever the File menu is opened.
	 */
	private void addPlaybackOptions(JMenu fileJMenu) {
		loopScript = new JCheckBoxMenuItem(TEXT_LOOP);
		loopScript.setToolTipText(TIP_LOOP);
		loopScript.addActionListener(e -> {
			ScriptTab tab = getActiveScriptTab();
			if(tab != null) tab.getScript().setLoop(loopScript.isSelected());
		});
		fileJMenu.add(loopScript);

		disconnectOnPause = new JCheckBoxMenuItem(TEXT_DISCONNECT, Script.DEFAULT_DISCONNECT_ON_PAUSE);
		disconnectOnPause.setToolTipText(TIP_DISCONNECT);
		disconnectOnPause.addActionListener(e -> {
			ScriptTab tab = getActiveScriptTab();
			if(tab != null) tab.getScript().setDisconnectOnPause(disconnectOnPause.isSelected());
		});
		fileJMenu.add(disconnectOnPause);

		hzMenu = new JMenu(TEXT_HZ_MENU + Script.DEFAULT_HZ + TEXT_HZ_UNIT);
		hzMenu.setToolTipText(TIP_HZ_MENU);
		ButtonGroup hzGroup = new ButtonGroup();
		hzPresetItems = new JRadioButtonMenuItem[HZ_PRESETS.length];
		for(int i = 0; i < HZ_PRESETS.length; i++) {
			final int hz = HZ_PRESETS[i];
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(hz + TEXT_HZ_UNIT);
			item.addActionListener(e -> setActiveScriptHz(hz));
			hzGroup.add(item);
			hzMenu.add(item);
			hzPresetItems[i] = item;
		}
		hzCustomItem = new JRadioButtonMenuItem(TEXT_HZ_CUSTOM);
		hzCustomItem.addActionListener(e -> askCustomHz());
		hzGroup.add(hzCustomItem);
		hzMenu.add(hzCustomItem);
		fileJMenu.add(hzMenu);

		fileJMenu.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				updatePlaybackOptions();
			}
			@Override
			public void menuDeselected(MenuEvent e) {}
			@Override
			public void menuCanceled(MenuEvent e) {}
		});
	}

	/** Refreshes the loop / disconnect / HZ menu items from the currently active script. */
	private void updatePlaybackOptions() {
		ScriptTab tab = getActiveScriptTab();
		loopScript.setEnabled(tab != null);
		disconnectOnPause.setEnabled(tab != null);
		hzMenu.setEnabled(tab != null);
		if(tab == null) return;

		Script script = tab.getScript();
		loopScript.setSelected(script.isLoop());
		disconnectOnPause.setSelected(script.isDisconnectOnPause());
		hzMenu.setText(TEXT_HZ_MENU + script.getHz() + TEXT_HZ_UNIT);

		boolean isPreset = false;
		for(int i = 0; i < HZ_PRESETS.length; i++) {
			boolean selected = HZ_PRESETS[i] == script.getHz();
			hzPresetItems[i].setSelected(selected);
			isPreset |= selected;
		}
		hzCustomItem.setSelected(!isPreset);
		hzCustomItem.setText(isPreset ? TEXT_HZ_CUSTOM : TEXT_HZ_CUSTOM_ACTIVE_PREFIX + script.getHz() + TEXT_HZ_UNIT + TEXT_HZ_CUSTOM_ACTIVE_SUFFIX);
	}

	private void setActiveScriptHz(int hz) {
		ScriptTab tab = getActiveScriptTab();
		if(tab != null) tab.getScript().setHz(hz);
	}

	private void askCustomHz() {
		ScriptTab tab = getActiveScriptTab();
		if(tab == null) return;

		Object input = JOptionPane.showInputDialog(mainEditorWindow, String.format(TEXT_HZ_DIALOG, Script.MIN_HZ, Script.MAX_HZ),
			TITLE_HZ_DIALOG, JOptionPane.PLAIN_MESSAGE, null, null, String.valueOf(tab.getScript().getHz()));
		if(input == null) return; // cancelled

		try {
			tab.getScript().setHz(Integer.parseInt(input.toString().trim()));
		} catch(IllegalArgumentException ex) { // NumberFormatException is one too
			JOptionPane.showMessageDialog(mainEditorWindow, String.format(TEXT_HZ_ERROR, Script.MIN_HZ, Script.MAX_HZ), TITLE_HZ_ERROR, JOptionPane.ERROR_MESSAGE);
		}
	}

	private JMenu createEditMenu(MainEditorWindow mainEditorWindow){
		JMenu editJMenu = new JMenu("Edit");

		undo = editJMenu.add("Undo");
		undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut));
		undo.addActionListener(e -> getActiveScriptTab().undo());

		redo = editJMenu.add("Redo");
		updateRedoAccelerator(Settings.INSTANCE.redoKeybind.get());
		redo.addActionListener(e -> getActiveScriptTab().redo());
		Settings.INSTANCE.redoKeybind.attachListener(this::updateRedoAccelerator);

		editJMenu.addSeparator();

		cut = editJMenu.add("Cut");
		cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcut));
		cut.addActionListener(e -> getActiveScriptTab().getPianoRoll().cut());

		copy = editJMenu.add("Copy");
		copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut));
		copy.addActionListener(e -> getActiveScriptTab().getPianoRoll().copy());

		paste = editJMenu.add("Paste");
		paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcut));
		paste.addActionListener(e -> {
			try {
				getActiveScriptTab().getPianoRoll().paste();
			} catch (IOException | UnsupportedFlavorException ioException) {
				ioException.printStackTrace();
			}
		});

		replace = editJMenu.add("Replace");
		replace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcut));
		replace.addActionListener(e -> {
			try {
				getActiveScriptTab().getPianoRoll().replace();
			} catch (IOException | UnsupportedFlavorException ioException) {
				ioException.printStackTrace();
			}
		});

		deleteLines = editJMenu.add("Delete");
		deleteLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
		deleteLines.addActionListener(e -> getActiveScriptTab().getPianoRoll().deleteSelectedRows());

		deleteLines = editJMenu.add("Clear lines");
		deleteLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
		deleteLines.addActionListener(e -> getActiveScriptTab().getPianoRoll().clearSelectedRows());

		selectLines = editJMenu.add("Select lines");
		selectLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcut));
		selectLines.addActionListener(e -> mainEditorWindow.selectLines());

		addLine = editJMenu.add("Add line");
		addLine.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcut | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
		addLine.addActionListener(e -> mainEditorWindow.addSingleEmptyRow());

		addLines = editJMenu.add("Add multiple lines");
		addLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcut | InputEvent.SHIFT_DOWN_MASK));
		addLines.addActionListener(e -> mainEditorWindow.addMultipleEmptyRows());

		editJMenu.addSeparator();

		settings = editJMenu.add("Settings");
		settings.addActionListener(e -> mainEditorWindow.openSettings());

		return editJMenu;
	}

	private JMenu createViewMenu(){
		JMenu viewJMenu = new JMenu("View");

		ObservableProperty<Boolean> darkThemeSetting = Settings.INSTANCE.darkTheme;
		darkTheme = new JCheckBoxMenuItem("Toggle Dark Theme", darkThemeSetting.get());
		viewJMenu.add(darkTheme);
		darkTheme.addItemListener(e -> darkThemeSetting.set(darkTheme.getState()));
		darkThemeSetting.attachListener(darkTheme::setState);

		return viewJMenu;
	}

	private JMenu createAboutMenu(){
		JMenu aboutMenu = new JMenu("About");
		JMenuItem buildTime = aboutMenu.add(BuildInfo.getDisplayText());
		buildTime.setEnabled(false);
		return aboutMenu;
	}

	private ScriptTab getActiveScriptTab() {
		return mainEditorWindow.getActiveScriptTab();
	}

	public void updateUndoMenu(boolean enableUndo, boolean enableRedo) {
		undo.setEnabled(enableUndo);
		redo.setEnabled(enableRedo);
	}

	public void updateRedoAccelerator(Settings.RedoKeybind keybind) {
		switch(keybind) {
			case CTRL_SHIFT_Z: redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut | InputEvent.SHIFT_DOWN_MASK)); break;
			case CTRL_Y: redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut)); break;
			default: System.err.println("setting undefined redokeybind! "+keybind);
		}
	}

	public void enableScriptRelatedInputs(boolean closed) {
		save.setEnabled(closed);
		saveAs.setEnabled(closed);
		saveCopy.setEnabled(closed);
		undo.setEnabled(closed);
		redo.setEnabled(closed);
		cut.setEnabled(closed);
		copy.setEnabled(closed);
		paste.setEnabled(closed);
		replace.setEnabled(closed);
		deleteLines.setEnabled(closed);
		selectLines.setEnabled(closed);
		addLine.setEnabled(closed);
	}
}
