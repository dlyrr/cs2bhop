package com.santi.cs2bhop;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.item.ModItems;
import com.santi.cs2bhop.net.BhopPayloads;
import com.santi.cs2bhop.progress.BhopCommand;
import com.santi.cs2bhop.progress.BhopTracker;
import com.santi.cs2bhop.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cs2Bhop implements ModInitializer {

    public static final String MOD_ID = "cs2bhop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BhopConfig config = BhopConfig.get();

        ModItems.register();
        ModSounds.register();

        PayloadTypeRegistry.clientboundPlay()
                .register(BhopPayloads.ProgressSync.TYPE, BhopPayloads.ProgressSync.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(BhopPayloads.ReleaseShockwave.TYPE, BhopPayloads.ReleaseShockwave.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                BhopPayloads.ReleaseShockwave.TYPE,
                (payload, context) -> context.server().execute(() -> BhopTracker.releaseShockwave(context.player())));

        ServerTickEvents.END_SERVER_TICK.register(BhopTracker::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BhopTracker.syncNow(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> BhopTracker.clear(handler.getPlayer().getUUID()));

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> BhopCommand.register(dispatcher));

        LOGGER.info(
                "CS2 movement loaded (autobhop={}, stamina={}, mob bhop={}, {} substeps)",
                config.autoBunnyHopping,
                config.stamina,
                config.mobBhop,
                config.effectiveSubticks());
    }
}
