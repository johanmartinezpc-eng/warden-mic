package com.wardenmic.client;

import com.wardenmic.NoisePayload;
import com.wardenmic.WardenMic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class WardenMicClient implements ClientModInitializer {

    private static final int HISTORY = 80;
    private static final float[] history = new float[HISTORY];
    private static int tickCounter = 0;

    // Colores ARGB
    private static final int PANEL_BG   = 0xAA000000; // negro transparente
    private static final int WAVE_COLOR = 0xFF55CCFF; // azul celeste
    private static final int LINE_COLOR = 0x5555CCFF; // línea central tenue

    private static final MicCapture MIC = new MicCapture();

    @Override
    public void onInitializeClient() {
        MIC.start();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            float level = MIC.getLevel();

            // Desplaza el historial una posición (20 veces por segundo)
            System.arraycopy(history, 1, history, 0, HISTORY - 1);
            history[HISTORY - 1] = level;

            // Envía el nivel al servidor 10 veces por segundo, solo si hay ruido relevante
            tickCounter++;
            if (client.player != null && tickCounter % 2 == 0
                    && level >= WardenMic.THRESHOLD
                    && ClientPlayNetworking.canSend(NoisePayload.TYPE)) {
                ClientPlayNetworking.send(new NoisePayload(level));
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("wardenmic", "mic_wave"),
                (GuiGraphicsExtractor g, DeltaTracker delta) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();

            int w = HISTORY * 2;   // 2 px por muestra
            int h = 40;
            int x = 8;
            int y = mc.getWindow().getGuiScaledHeight() - h - 30;

            // Panel negro transparente
            g.fill(x - 4, y - 4, x + w + 4, y + h + 4, PANEL_BG);

            // Línea central
            int cy = y + h / 2;
            g.fill(x, cy, x + w, cy + 1, LINE_COLOR);

            // Onda: barras verticales simétricas respecto a la línea central
            for (int i = 0; i < HISTORY; i++) {
                int half = Math.max(1, Math.round(history[i] * (h / 2f)));
                int bx = x + i * 2;
                g.fill(bx, cy - half, bx + 1, cy + half, WAVE_COLOR);
            }
        });
    }
}
