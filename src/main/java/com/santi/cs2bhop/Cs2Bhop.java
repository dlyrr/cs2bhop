package com.santi.cs2bhop;

import com.santi.cs2bhop.boss.PhoonBossFight;
import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.item.ModItems;
import com.santi.cs2bhop.net.BhopPayloads;
import com.santi.cs2bhop.progress.BhopCommand;
import com.santi.cs2bhop.progress.BhopTracker;
import com.santi.cs2bhop.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cs2Bhop implements ModInitializer {

    public static final String MOD_ID = "cs2bhop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BhopConfig config = BhopConfig.get();

        com.santi.cs2bhop.entity.ModEntities.register();
        ModItems.register();
        ModSounds.register();
        com.santi.cs2bhop.boss.BossChoreography.load();

        PayloadTypeRegistry.clientboundPlay()
                .register(BhopPayloads.ProgressSync.TYPE, BhopPayloads.ProgressSync.CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(BhopPayloads.ReleaseShockwave.TYPE, BhopPayloads.ReleaseShockwave.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                BhopPayloads.ReleaseShockwave.TYPE,
                (payload, context) -> context.server().execute(() -> BhopTracker.releaseShockwave(context.player())));

        PayloadTypeRegistry.clientboundPlay().register(BhopPayloads.BossSync.TYPE, BhopPayloads.BossSync.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BhopTracker.tick(server);
            PhoonBossFight.tickActive();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> PhoonBossFight.abortActive());

        // PHOON is invulnerable unless it is tired, so the window is the only offensive opening.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            PhoonBossFight fight = PhoonBossFight.current();
            if (fight == null || !fight.isBoss(entity.getUUID())) {
                return true;
            }

            if (fight.canBeHit()) {
                return true;
            }

            if (source.getEntity() instanceof ServerPlayer attacker) {
                fight.onHitRefused(attacker);
            }
            return false;
        });

        // Hits either way move the scoreboard, so the fight is a race you can interfere with.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
            PhoonBossFight fight = PhoonBossFight.current();
            if (fight == null) {
                return;
            }

            if (entity instanceof ServerPlayer hurt && fight.isFighter(hurt.getUUID())) {
                if (source.getEntity() != null && fight.isBoss(source.getEntity().getUUID())) {
                    fight.onPlayerHit(hurt);
                }
            } else if (fight.isBoss(entity.getUUID())
                    && source.getEntity() instanceof ServerPlayer attacker
                    && fight.isFighter(attacker.getUUID())) {
                fight.onBossHit(attacker);
            }
        });

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

