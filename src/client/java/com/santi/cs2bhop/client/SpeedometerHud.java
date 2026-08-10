package com.santi.cs2bhop.client;

import com.santi.cs2bhop.config.BhopConfig;
import com.santi.cs2bhop.physics.MoveState;
import com.santi.cs2bhop.progress.BhopLevels;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * A {@code cl_showpos}-style readout: speed in Source units per second, gain on the current jump,
 * the live hop chain, and your level with a progress bar toward the next one.
 */
public class SpeedometerHud implements HudElement {

    private static final int PANEL_BG = 0x90000000;
    private static final int BAR_BG = 0x60FFFFFF;
    private static final int LABEL = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GAIN = 0xFF6BE06B;
    private static final int LOSS = 0xFFE06B6B;
    private static final int ACCENT = 0xFFE0A030;
    private static final int LEVEL_COLOUR = 0xFF52C4E0;
    private static final int BIOME_COLOUR = 0xFF9BE06B;
    private static final int MAXED = 0xFFFFD24A;

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

        int level = ClientProgress.level();
        int streak = ClientProgress.streak();
        boolean maxed = level >= BhopLevels.MAX_LEVEL;

        String speedText = "%.0f".formatted(speed);
        String blockText = "%.2f blocks/s".formatted(blocksPerSecond);
        String gainText = "%+.0f".formatted(gain);
        String levelText = "LVL %d".formatted(level);

        int panelWidth = 118;
        int panelHeight = 56 + (streak > 0 ? 11 : 0) + (ClientProgress.inBhopBiome() ? 10 : 0);
        int x = (graphics.guiWidth() - panelWidth) / 2;
        int y = graphics.guiHeight() - panelHeight - 68;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);

        int textX = x + 6;
        int textY = y + 5;

        graphics.text(font, speedText, textX, textY, WHITE, true);
        graphics.text(font, " u/s", textX + font.width(speedText), textY, LABEL, true);

        if (!player.onGround() && Math.abs(gain) >= 1.0) {
            graphics.text(
                    font, gainText, x + panelWidth - 6 - font.width(gainText), textY, gain > 0.0 ? GAIN : LOSS, true);
        }

        textY += 11;
        graphics.text(font, blockText, textX, textY, LABEL, true);

        textY += 13;
        graphics.text(font, levelText, textX, textY, maxed ? MAXED : LEVEL_COLOUR, true);

        String pointsText = "%,d".formatted(ClientProgress.points());
        graphics.text(font, pointsText, x + panelWidth - 6 - font.width(pointsText), textY, LABEL, true);

        // Level progress bar.
        textY += 11;
        int barWidth = panelWidth - 12;
        graphics.fill(textX, textY, textX + barWidth, textY + 3, BAR_BG);
        double progress = maxed ? 1.0 : BhopLevels.levelProgress(ClientProgress.points());
        int filled = (int) Math.round(barWidth * Math.max(0.0, Math.min(1.0, progress)));
        if (filled > 0) {
            graphics.fill(textX, textY, textX + filled, textY + 3, maxed ? MAXED : LEVEL_COLOUR);
        }

        textY += 8;
        if (streak > 0) {
            graphics.text(font, streak + " hop chain", textX, textY, ACCENT, true);
            textY += 11;
        }

        if (ClientProgress.inBhopBiome()) {
            graphics.text(font, "BHOP FLATS  x%.1f".formatted(config.bhopBiomePointMultiplier), textX, textY, BIOME_COLOUR, true);
        }
    }
}
