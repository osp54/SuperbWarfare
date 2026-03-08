package com.atsuishio.superbwarfare.client.sound;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ModSoundInstances {

    private static final Map<UUID, Long> ENGINE_SOUND_GUARD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TRACK_SOUND_GUARD = new ConcurrentHashMap<>();

    private static boolean shouldPlay(Map<UUID, Long> guard, VehicleEntity vehicle, long cooldownMs) {
        long now = System.currentTimeMillis();
        UUID uuid = vehicle.getUUID();
        Long last = guard.get(uuid);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        guard.put(uuid, now);
        return true;
    }

    public static void init() {
        VehicleEntity.playTrackSound = vehicle -> {
            if (shouldPlay(TRACK_SOUND_GUARD, vehicle, 500L)) {
                Minecraft.getInstance().getSoundManager().play(new VehicleSoundInstance.TrackSound(vehicle));
            }
        };
        VehicleEntity.playEngineSound = vehicle -> {
            if (shouldPlay(ENGINE_SOUND_GUARD, vehicle, 500L)) {
                Minecraft.getInstance().getSoundManager().play(new VehicleSoundInstance.EngineSound(vehicle));
            }
        };
        VehicleEntity.playSwimSound = vehicle -> Minecraft.getInstance().getSoundManager().play(new VehicleSoundInstance.SwimSound(vehicle));
        VehicleEntity.playHornSound = vehicle -> Minecraft.getInstance().getSoundManager().play(new HornSoundInstance.VehicleHornSound(vehicle));
//        VehicleEntity.playInCarMusic = vehicle -> {
//            if (NetMusicCompatHolder.canPlayMusic(vehicle)) {
//                NetMusicCompatHolder.playMusic(vehicle);
//            } else {
//                Minecraft.getInstance().getSoundManager().play(new InCarMusicInstance.InCarMusicSound(vehicle));
//            }
//        };

        VehicleEntity.playFireSound = vehicle -> Minecraft.getInstance().getSoundManager().play(new VehicleFireSoundInstance.VehicleFireSound(vehicle));

        FastThrowableProjectile.playFlySound = entity -> Minecraft.getInstance().getSoundManager().play(new FastProjectileSoundInstance.FlySound(entity));
    }
}
