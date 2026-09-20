package io.github.jadefalke2.script;

import io.github.jadefalke2.InputLine;
import io.github.jadefalke2.Script;
import io.github.jadefalke2.stickRelatedClasses.StickPosition;
import io.github.jadefalke2.util.Button;
import io.github.jadefalke2.util.CorruptedScriptException;
import io.github.jadefalke2.util.Logger;
import io.github.jadefalke2.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class NXTas {

	public static void write(Script script, File file) throws IOException {
		Logger.log("saving script to " + file.getAbsolutePath());
		Util.writeFile(write(script), file);
	}

	public static String write(Script script) {
		InputLine[] inputLines = script.getLines();

		// header: playback options for the Pico 2 firmware
		StringBuilder sb = new StringBuilder();
		sb.append("LOOP ").append(script.isLoop() ? "TRUE" : "FALSE").append("\n");
		sb.append("HZ ").append(script.getHz()).append("\n");
		sb.append("DISCONNECT ").append(script.isDisconnectOnPause() ? "TRUE" : "FALSE").append("\n");

		int lastWritten = -1;
		for (int i = 0; i < inputLines.length; i++) {
			if (inputLines[i].isEmpty()) continue;
			sb.append(inputLines[i].getFull(i)).append("\n");
			lastWritten = i;
		}

		// Empty rows are normally left out of the file. When looping, trailing empty rows are part of the
		// loop's length though (waiting before it restarts), so write the last row out explicitly.
		if (script.isLoop() && inputLines.length > 0 && lastWritten < inputLines.length - 1) {
			int last = inputLines.length - 1;
			sb.append(inputLines[last].getFull(last)).append("\n");
		}
		return sb.toString();
	}

	public static Script read(File file) throws CorruptedScriptException, IOException {
		Script s = read(Util.fileToString(file));
		s.setFile(file, Format.nxTAS);
		return s;
	}

	public static Script read(String script) throws CorruptedScriptException {
		List<InputLine> inputLines = new ArrayList<>();
		String[] lines = script.split("\n");

		int currentFrame = 0;
		boolean loop = false;
		int hz = Script.DEFAULT_HZ;
		boolean disconnectOnPause = Script.DEFAULT_DISCONNECT_ON_PAUSE;
		boolean inHeader = true;

		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;

			// Header directives ("LOOP TRUE", "HZ 60", "DISCONNECT TRUE") - anything before the first frame line that doesn't start with a digit
			if (!Character.isDigit(line.charAt(0))) {
				if (!inHeader) {
					throw new CorruptedScriptException("Unexpected line after the first frame: " + line, currentFrame);
				}
				String[] parts = line.split("\\s+");
				String key = parts[0].toUpperCase();
				if (key.equals("LOOP") && parts.length == 2 && (parts[1].equalsIgnoreCase("TRUE") || parts[1].equalsIgnoreCase("FALSE"))) {
					loop = parts[1].equalsIgnoreCase("TRUE");
				} else if (key.equals("HZ") && parts.length == 2 && isValidHz(parts[1])) {
					hz = Integer.parseInt(parts[1]);
				} else if (key.equals("DISCONNECT") && parts.length == 2 && (parts[1].equalsIgnoreCase("TRUE") || parts[1].equalsIgnoreCase("FALSE"))) {
					disconnectOnPause = parts[1].equalsIgnoreCase("TRUE");
				} else {
					throw new CorruptedScriptException("Invalid header line: " + line, -1);
				}
				continue;
			}
			inHeader = false;

			InputLine currentInputLine = readLine(line);
			int frame = Integer.parseInt(line.split(" ")[0]);

			if (frame < currentFrame){
				throw new CorruptedScriptException("Line numbers misordered", currentFrame);
			}

			while(currentFrame < frame){
				inputLines.add(InputLine.getEmpty());
				currentFrame++;
			}

			inputLines.add(currentInputLine);
			currentFrame++;
		}
		return new Script(inputLines.toArray(new InputLine[0]), 0, loop, hz, disconnectOnPause);
	}

	private static boolean isValidHz(String value) {
		try {
			int hz = Integer.parseInt(value);
			return hz >= Script.MIN_HZ && hz <= Script.MAX_HZ;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static InputLine readLine(String full) throws CorruptedScriptException {
		if (full.isEmpty()){
			throw new CorruptedScriptException("Empty InputLine", -1);
		}

		int frame = 0;
		try {
			String[] components = full.split(" ");

			EnumSet<Button> buttonSet = EnumSet.noneOf(Button.class);
			frame = Integer.parseInt(components[0]);
			String buttons = components[1];
			String[] buttonsPressed = buttons.split(";");

			ArrayList<String> ignoredInvalidButtons = new ArrayList<>();
			for (String s : buttonsPressed) {
				if(!s.equals("NONE")) {
					try {
						buttonSet.add(Button.valueOf(s));
					} catch (IllegalArgumentException e) {
						if(ignoredInvalidButtons.contains(s)){
							continue;
						}
						// create instance of exception to cause popup, but do not throw
						new CorruptedScriptException("Unknown button: " + s + ", ignoring all subsequent occurences", frame, e);
						ignoredInvalidButtons.add(s);
					}
				}
			}

			StickPosition stickL = new StickPosition(components[2]);
			StickPosition stickR = new StickPosition(components[3]);

			return new InputLine(buttonSet, stickL, stickR);
		} catch (Exception e) {
			throw new CorruptedScriptException("Script corrupted", frame, e);
		}
	}

}
