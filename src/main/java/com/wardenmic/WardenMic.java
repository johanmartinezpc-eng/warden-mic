package com.wardenmic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;

import java.util.List;

public class WardenMic implements ModInitializer {

    /** Por debajo de este nivel (0-1) se considera ruido de fondo y los Warden no oyen nada. */
    public static final float THRESHOLD = 0.35f;
    /** Radio (bloques) con un ruido apenas sobre el umbral. */
    public static final double MIN_RADIUS = 2.0;
    /** Radio (bloques) con un grito al máximo. */
    public static final double MAX_RADIUS = 30.0;
    /** Nivel (0-1) a partir del cual el Warden además se enfurece contigo. */
    public static final float ANGER_LEVEL = 0.8f;

    public static double radiusFor(float level) {
        if (level < THRESHOLD) return 0;
        double t = (level - THRESHOLD) / (1.0 - THRESHOLD);
        return MIN_RADIUS + t * (MAX_RADIUS - MIN_RADIUS);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(NoisePayload.TYPE, NoisePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(NoisePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            float level = Math.max(0f, Math.min(1f, payload.level()));
            double radius = radiusFor(level);
            if (radius <= 0) return;

            ServerLevel world = (ServerLevel) player.level();
            List<Warden> wardens = world.getEntitiesOfClass(
                    Warden.class, player.getBoundingBox().inflate(radius));

            for (Warden warden : wardens) {
                if (warden.distanceTo(player) > radius) continue;
                // El Warden se orienta hacia la posición donde hiciste ruido
                WardenAi.setDisturbanceLocation(warden, BlockPos.containing(player.position()));
                if (level >= ANGER_LEVEL) {
                    warden.increaseAngerAt(player, 20, false);
                }
            }
        });
    }
}
