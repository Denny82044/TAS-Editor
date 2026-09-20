package io.github.jadefalke2.components;

import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Talks to a Pico running the TAS serial-upload firmware. This mirrors the
 * exact same wire protocol as the standalone upload.py script, so either
 * one can talk to the device interchangeably:
 *
 *   host -> device: "PING\n"             device -> host: "PONG <version>\n"
 *   host -> device: "UPLOAD <length>\n"  device -> host: "READY\n"
 *   host -> device: <length> raw bytes                  "OK <length>\n" / "ERROR <reason>\n"
 *
 * There's no USB VID/PID to filter on - the gamepad interface deliberately
 * spoofs a Hori controller ID for Switch compatibility - so every serial
 * port on the machine is probed with PING and whichever one answers is
 * used. This is the same approach upload.py takes.
 */
public class PicoUploader {

	private static final int BAUD = 115200;
	private static final int PING_TIMEOUT_MS = 400;
	private static final int UPLOAD_TIMEOUT_MS = 5000;

	public static class UploadException extends Exception {
		public UploadException(String message) {
			super(message);
		}
	}

	/** Probes every serial port and returns the system name of the first one that answers PING, or null if none do. */
	public static String findDevicePortName() {
		for (SerialPort port : SerialPort.getCommPorts()) {
			port.setBaudRate(BAUD);
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, PING_TIMEOUT_MS, 0);
			if (!port.openPort()) {
				continue;
			}
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(port.getInputStream(), StandardCharsets.US_ASCII));
				writeLine(port, "PING");
				String resp = reader.readLine();
				if (resp != null && resp.startsWith("PONG")) {
					return port.getSystemPortName();
				}
			} catch (IOException ignored) {
				// wrong port, or busy with something else - just move on
			} finally {
				port.closePort();
			}
		}
		return null;
	}

	/** Sends movieText (already in the firmware's movie.txt format, e.g. from NXTas.write(script)) to the device. */
	public static void upload(String portName, String movieText) throws UploadException {
		SerialPort port = SerialPort.getCommPort(portName);
		port.setBaudRate(BAUD);
		port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, UPLOAD_TIMEOUT_MS, 0);
		if (!port.openPort()) {
			throw new UploadException("Could not open " + portName);
		}
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(port.getInputStream(), StandardCharsets.US_ASCII));
			byte[] data = movieText.getBytes(StandardCharsets.UTF_8);

			writeLine(port, "UPLOAD " + data.length);
			String ready = reader.readLine();
			if (ready == null || !ready.equals("READY")) {
				throw new UploadException("Device wasn't ready: " + ready);
			}

			OutputStream out = port.getOutputStream();
			out.write(data);
			out.flush();

			String result = reader.readLine();
			if (result == null || !result.startsWith("OK")) {
				throw new UploadException("Upload failed: " + result);
			}
		} catch (IOException e) {
			throw new UploadException("Serial I/O error: " + e.getMessage());
		} finally {
			port.closePort();
		}
	}

	private static void writeLine(SerialPort port, String line) throws IOException {
		OutputStream out = port.getOutputStream();
		out.write((line + "\n").getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}
}
