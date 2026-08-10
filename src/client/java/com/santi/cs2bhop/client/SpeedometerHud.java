package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.physics.MoveState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * A {@code cl_showpos}-style readout: current speed in Source units per second, how much you gained
 * or lost on the current jump, and the hop streak.
 *
 * <p>The gain figure is the one worth watching. Positive means your strafing beat the air-accel
 * clamp; negative means you landed flat and paid friction.
 */
public class SpeedometerHud implements HudElement {

    private static final int PANEL_BG = 0x90000000;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GAIN = 0xFF6BE06B;
    private static final int LOSS = 0xFFE06B6B;
    private static final int ACCENT = 0xFFE0A030;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        BhopConfig config = BhopConfig.get();
        if (!config.enabled || !config.hud) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        MoveState state = Cs2BhopClient.state();
        Font font = minecraft.font;

        double speed = state.previousSpeed;
        double blocksPerSecond = speed * config.unitsToBlocks;
        double gain = player.onGround() ? 0.0 : speed - state.takeoffSpeed;

        String speedText = "%.0f".formatted(speed);
        String unitText = " u/s";
        String blockText = "%.2f blocks/s".formatted(blocksPerSecond);
        String gainText = "%+.0f".formatted(gain);

        int panelWidth = 108;
        int panelHeight = state.hopStreak > 1 ? 44 : 34;
        int x = (graphics.guiWidth() - panelWidth) / 2;
        int y = graphics.guiHeight() - panelHeight - 68;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);

        int textX = x + 6;
        int textY = y + 5;

        graphics.text(font, speedText, textX, textY, WHITE, true);
        graphics.text(font, unitText, textX + font.width(speedText), textY, LABEL, true);

        if (!player.onGround() && Math.abs(gain) >= 1.0) {
            graphics.text(
                    font,
                    gainText,
                    x + panelWidth - 6 - font.width(gainText),
                    textY,
                    gain > 0.0 ? GAIN : LOSS,
                    true);
        }

        graphics.text(font, blockText, textX, textY + 11, LABEL, true);

        if (state.hopStreak > 1) {
            graphics.text(font, state.hopStreak + " hop streak", textX, textY + 22, ACCENT, true);
        }
    }
}
