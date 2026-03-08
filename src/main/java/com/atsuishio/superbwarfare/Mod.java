package com.atsuishio.superbwarfare;

import com.atsuishio.superbwarfare.block.entity.FuMO25BlockEntity;
import com.atsuishio.superbwarfare.client.MouseMovementHandler;
import com.atsuishio.superbwarfare.client.molang.MolangVariable;
import com.atsuishio.superbwarfare.client.sound.ModSoundInstances;
import com.atsuishio.superbwarfare.compat.coldsweat.ColdSweatCompatHandler;
import com.atsuishio.superbwarfare.compat.tacz.TACZGunEventHandler;
import com.atsuishio.superbwarfare.config.ClientConfig;
import com.atsuishio.superbwarfare.config.CommonConfig;
import com.atsuishio.superbwarfare.config.ServerConfig;
import com.atsuishio.superbwarfare.data.CustomData;
import com.atsuishio.superbwarfare.init.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.network.SerializableDataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@net.minecraftforge.fml.common.Mod(Mod.MODID)
public class Mod {

    public static final String MODID = "superbwarfare";
    public static final String ATTRIBUTE_MODIFIER = "superbwarfare_attribute_modifier";

    public static final Logger LOGGER = LogManager.getLogger(Mod.class);

    public Mod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.init());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.init());
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.init());

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        ModPerks.register(bus);
        ModSerializers.REGISTRY.register(bus);
        ModSounds.REGISTRY.register(bus);
        ModBlocks.REGISTRY.register(bus);
        ModBlockEntities.REGISTRY.register(bus);
        ModItems.register(bus);
        ModEntities.REGISTRY.register(bus);
        ModTabs.TABS.register(bus);
        ModMobEffects.REGISTRY.register(bus);
        ModParticleTypes.REGISTRY.register(bus);
        ModPotions.POTIONS.register(bus);
        ModMenuTypes.REGISTRY.register(bus);
        ModVillagers.register(bus);
        ModRecipes.register(bus);
        ModCommandArguments.COMMAND_ARGUMENT_TYPES.register(bus);

        bus.addListener(this::onCommonSetup);
        bus.addListener(this::onClientSetup);
        bus.addListener(ModItems::registerDispenserBehavior);

        registerDataTickets();

        if (TACZGunEventHandler.compatCondition()) {
            MinecraftForge.EVENT_BUS.addListener(TACZGunEventHandler::entityHurtByTACZGun);
        }
        if (ColdSweatCompatHandler.hasMod()) {
            MinecraftForge.EVENT_BUS.addListener(ColdSweatCompatHandler::onPlayerInVehicle);
        }

        MinecraftForge.EVENT_BUS.register(this);

        CustomData.load();
    }

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static final ConcurrentLinkedQueue<AbstractMap.SimpleEntry<Runnable, Integer>> SERVER_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<AbstractMap.SimpleEntry<Runnable, Integer>> CLIENT_QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_CLIENT_QUEUE_SIZE = 4096;

    public static void queueServerWork(int tick, Runnable action) {
        SERVER_QUEUE.add(new AbstractMap.SimpleEntry<>(action, tick));
    }

    public static void queueClientWork(int tick, Runnable action) {
        if (CLIENT_QUEUE.size() >= MAX_CLIENT_QUEUE_SIZE) {
            return;
        }
        CLIENT_QUEUE.add(new AbstractMap.SimpleEntry<>(action, tick));
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            processQueue(SERVER_QUEUE);
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            processQueue(CLIENT_QUEUE);
        }
    }

    private static void processQueue(ConcurrentLinkedQueue<AbstractMap.SimpleEntry<Runnable, Integer>> queue) {
        int queuedActions = queue.size();
        for (int i = 0; i < queuedActions; i++) {
            AbstractMap.SimpleEntry<Runnable, Integer> work = queue.poll();
            if (work == null) {
                return;
            }

            int remainingTicks = work.getValue() - 1;
            if (remainingTicks <= 0) {
                work.getKey().run();
            } else {
                work.setValue(remainingTicks);
                queue.add(work);
            }
        }
    }

    public void onCommonSetup(final FMLCommonSetupEvent event) {
        com.atsuishio.superbwarfare.network.NetworkRegistry.register();
    }

    public void onClientSetup(final FMLClientSetupEvent event) {
        MouseMovementHandler.init();
        MolangVariable.register();
        event.enqueueWork(ModSoundInstances::init);
    }

    private void registerDataTickets() {
        FuMO25BlockEntity.FUMO25_TICK = GeckoLibUtil.addDataTicket(SerializableDataTicket.ofInt(loc("fumo25_tick")));
    }
}
