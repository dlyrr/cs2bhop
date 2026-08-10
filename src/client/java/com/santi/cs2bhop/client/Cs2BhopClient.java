package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.physics.MoveState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cs2BhopClient implements ClientModInitializer {

    public static final String MOD_ID = "cs2bhop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * There is exactly one local player, so one state object is enough. It is reset whenever the
     * player instance changes (respawn, world switch, server change).
     */
    private static final MoveState STATE = new MoveState();

    private static LocalPlayer lastPlayer;

    private static KeyMapping toggleKey;
    private static KeyMapping autoBhopKey;
    private static KeyMapping hudKey;

    public static MoveState state() {
        return STATE;
    }

    @Override
    public void onInitializeClient() {
        BhopConfig config = BhopConfig.get();

        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.cs2bhop.toggle", GLFW.GLFW_KEY_B, KeyMapping.Category.MOVEMENT));
        autoBhopKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.cs2bhop.autobhop", GLFW.GLFW_KEY_N, KeyMapping.Category.MOVEMENT));
        hudKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.cs2bhop.hud", GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MOVEMENT));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "speedometer"), new SpeedometerHud());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != lastPlayer) {
                lastPlayer = client.player;
                STATE.reset();
            }

            while (toggleKey.consumeClick()) {
                config.enabled = !config.enabled;
                config.save();
                announce(client, "CS2 movement", config.enabled);
            }

            while (autoBhopKey.consumeClick()) {
                config.autoBunnyHopping = !config.autoBunnyHopping;
                config.save();
                announce(client, "Autobhop", config.autoBunnyHopping);
            }

            while (hudKey.consumeClick()) {
                config.hud = !config.hud;
                config.save();
                announce(client, "Speedometer", config.hud);
            }
        });

        LOGGER.info(
                "CS2 movement ready (autobhop={}, stamina={}, {} u/s, {} substeps)",
                config.autoBunnyHopping,
                config.stamina,
                config.sv_maxspeed,
                config.effectiveSubticks());
    }

    private static void announce(Minecraft client, String what, boolean on) {
        if (client.gui != null) {
            client.gui.setOverlayMessage(Component.literal(what + (on ? ": on" : ": off")), false);
        }
    }
}
