package io.github.jadefalke2;

import io.github.jadefalke2.components.TasFileChooser;
import io.github.jadefalke2.script.Format;
import io.github.jadefalke2.stickRelatedClasses.JoystickPanel;
import io.github.jadefalke2.stickRelatedClasses.StickPosition;
import io.github.jadefalke2.util.*;

import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Script {

	/**
	 * returns an empty string with a specified length of lines
	 * @param amount the number of lines
	 * @return the created script
	 */
	public static Script getEmptyScript (int amount){
		Script tmp;
		try {
			tmp = new Script();
		} catch (IOException | CorruptedScriptException e) {
			throw new RuntimeException(e);
		}

		for (int i = 0; i < amount; i++){
			tmp.insertRow(i,InputLine.getEmpty());
		}
		tmp.dirty = false;

		return tmp;
	}


	private File file;
	private Format format;
	private final ArrayList<InputLine> inputLines;
	private boolean dirty;

	private final ArrayList<ScriptObserver> observers;

	private long editingMillis;
	private long lastEdit;

	// Playback options for the Pico 2 firmware, saved as the "LOOP" / "HZ" / "DISCONNECT" header lines of nxTAS scripts.
	public static final int DEFAULT_HZ = 60;
	public static final int MIN_HZ = 1;
	public static final int MAX_HZ = 1000; // must match the limit in the firmware's parseMovieHeader()
	public static final boolean DEFAULT_DISCONNECT_ON_PAUSE = true;
	private boolean loop;
	private int hz;
	private boolean disconnectOnPause;

	public Script() throws IOException, CorruptedScriptException {
		this(new InputLine[0], 0);
	}
	public Script(InputLine[] lines, int editingSeconds) {
		this(lines, editingSeconds, false, DEFAULT_HZ, DEFAULT_DISCONNECT_ON_PAUSE);
	}
	public Script(InputLine[] lines, int editingSeconds, boolean loop, int hz) {
		this(lines, editingSeconds, loop, hz, DEFAULT_DISCONNECT_ON_PAUSE);
	}
	public Script(InputLine[] lines, int editingSeconds, boolean loop, int hz, boolean disconnectOnPause) {
		this.loop = loop;
		this.hz = hz;
		this.disconnectOnPause = disconnectOnPause;
		this.inputLines = new ArrayList<>(Arrays.asList(lines));
		this.dirty = false;
		this.observers = new ArrayList<>();
		this.editingMillis = editingSeconds * 1000L;

		attachObserver(new ScriptObserver() {
			@Override
			public void onDirtyChange(boolean dirty) {
				updateEditingSeconds();
			}
		});
		lastEdit = System.currentTimeMillis();
	}

	public void updateEditingSeconds() {
		// if last change was less than 5 minutes ago, add the time since then to editingSeconds
		long timeDiff = System.currentTimeMillis() - lastEdit;
		editingMillis += Math.min(timeDiff, 5*60*1000);
		lastEdit = System.currentTimeMillis();
	}

	public boolean closeScript(){
		if(!dirty){
			return true; //just close without issue if no changes happened
		}

		int result = JOptionPane.showConfirmDialog(null, "Save Project changes?", "Save before closing", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result == JOptionPane.YES_OPTION){
			//opens a new dialog that asks about saving, then close
			try {
				saveFile();
			} catch(IOException ioe) {
				JOptionPane.showMessageDialog(null, "Failed to save file!\nError: " + ioe.getMessage(), "Saving failed", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			return dirty;
		}
		return result == JOptionPane.NO_OPTION; //otherwise return false -> cancel
	}

	/**
	 * Saves the script (itself) to that last saved/opened file
	 */
	public void saveFile() throws IOException {
		if(file == null || format == null){
			saveFileAs();
			return;
		}

		writeToFile(file, format);
		setDirty(false);
	}

	private void writeToFile(File dest, Format format) throws IOException {
		Format.write(this, dest, format);
	}

	/**
	 * Opens a file selector popup and then saves the script (itself) to that file
	 */
	public void saveFileAs() throws IOException {
		TasFileChooser chooser = new TasFileChooser(Settings.INSTANCE.directory.get());
		File savedFile = chooser.getFile(false);
		Format format = chooser.getFormat();
		if(savedFile != null){
			Logger.log("saving file as " + savedFile.getAbsolutePath());
			setFile(savedFile, format);
			saveFile();
		}
	}

	public void saveFileCopy() throws IOException {
		TasFileChooser chooser = new TasFileChooser(Settings.INSTANCE.directory.get());
		File savedFile = chooser.getFile(false);
		Format format = chooser.getFormat();
		if(savedFile != null){
			Logger.log("saving copy of file as " + savedFile.getAbsolutePath());
			writeToFile(savedFile, format);
		}
	}

	public InputLine getLine(int row){
		return inputLines.get(row);
	}
	public InputLine[] getLines(int[] rows){
		return Arrays.stream(rows).mapToObj(inputLines::get).toArray(InputLine[]::new);
	}
	public InputLine[] getLines(){
		return inputLines.toArray(new InputLine[0]);
	}
	public int getNumLines() { return inputLines.size(); }

	public void replaceRow(int row, InputLine replacement) {
		inputLines.set(row, replacement);
		setDirty(true);
	}

	public void removeRow(int row){
		inputLines.remove(row);
		setDirty(true);
		updateLength();
	}

	public void insertRow(int row, InputLine line) {
		inputLines.add(row, line);
		setDirty(true);
		updateLength();
	}

	public void appendRow(InputLine line) {
		insertRow(inputLines.size(), line);
	}

	public void setButton(int row, Button button, boolean enabled) {
		boolean currentState = inputLines.get(row).buttons.contains(button);
		if(currentState == enabled) return;

		if(enabled) {
			inputLines.get(row).buttons.add(button);
		} else {
			inputLines.get(row).buttons.remove(button);
		}
		setDirty(true);
	}

	public void setStickPos(int row, JoystickPanel.StickType stickType, StickPosition position) {
		if(stickType == JoystickPanel.StickType.L_STICK)
			inputLines.get(row).setStickL(position);
		else
			inputLines.get(row).setStickR(position);
		setDirty(true);
	}

	public String getName() {
		return file == null ? "unnamed script" : file.getName();
	}

	public boolean isDirty() {
		return dirty;
	}

	private void setDirty(boolean dirty) {
		this.dirty = dirty;
		// timer expects this to be called on every change, not only if changes dirty state
		observers.forEach(c -> c.onDirtyChange(dirty));
	}
	public void setFile(File file, Format format) {
		this.file = file;
		this.format = format;
		observers.forEach(c -> c.onFileChange(file));
	}

	public void updateLength() {
		int after = inputLines.size();
		observers.forEach(c -> c.onLengthChange(after));
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		if(this.loop == loop) return;
		this.loop = loop;
		setDirty(true);
	}

	public int getHz() {
		return hz;
	}

	public void setHz(int hz) {
		if(hz < MIN_HZ || hz > MAX_HZ) throw new IllegalArgumentException("HZ must be between " + MIN_HZ + " and " + MAX_HZ);
		if(this.hz == hz) return;
		this.hz = hz;
		setDirty(true);
	}

	public boolean isDisconnectOnPause() {
		return disconnectOnPause;
	}

	public void setDisconnectOnPause(boolean disconnectOnPause) {
		if(this.disconnectOnPause == disconnectOnPause) return;
		this.disconnectOnPause = disconnectOnPause;
		setDirty(true);
	}

	public int getEditingSeconds() {
		return (int) (editingMillis / 1000);
	}

	public void attachObserver(ScriptObserver observer) {
		observers.add(observer);
	}
	public void detachObserver(ScriptObserver observer) {
		observers.remove(observer);
	}
}
