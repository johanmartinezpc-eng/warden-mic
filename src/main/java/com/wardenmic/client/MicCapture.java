package com.wardenmic.client;

import javax.sound.sampled.*;

/** Lee el micrófono en un hilo aparte y expone un nivel suavizado de 0.0 a 1.0. */
public class MicCapture {
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    private volatile float level = 0f;
    private volatile boolean running = false;
    private Thread thread;

    public float getLevel() { return level; }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "WardenMic-Capture");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[WardenMic] No se encontró micrófono compatible.");
                return;
            }
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();

            byte[] buf = new byte[1024]; // ~32 ms
            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;

                double sum = 0;
                int samples = n / 2;
                for (int i = 0; i < samples; i++) {
                    short s = (short) ((buf[2 * i + 1] << 8) | (buf[2 * i] & 0xFF));
                    double v = s / 32768.0;
                    sum += v * v;
                }
                double rms = Math.sqrt(sum / Math.max(1, samples));
                double db = 20 * Math.log10(Math.max(rms, 1e-6));
                // Mapea -60 dB .. 0 dB a 0 .. 1
                float target = (float) Math.max(0, Math.min(1, (db + 60) / 60));

                // Sube rápido, baja despacio
                float cur = level;
                level = target > cur ? target : cur * 0.85f + target * 0.15f;
            }
            line.stop();
            line.close();
        } catch (Exception e) {
            System.err.println("[WardenMic] Error con el micrófono: " + e);
        } finally {
            running = false;
        }
    }
}
