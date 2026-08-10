package com.santi.cs2bhop.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The fight scoreboard: {@code PHOON | 412 VS. santi | 388}, centred at the top, shaking.
 *
 * <p>The shake is driven by two sine waves at deliberately incommensurate frequencies, so it never
 * settles into a visible loop the way a single wave would. Amplitude tracks {@link
 * ClientBossState#tension()} — it twitches when you are comfortably ahead and genuinely rattles when
 * the scores converge or the clock runs down. Whoever is ahead is drawn brighter.
 */
public class BossHud implements HudElement {

    private static final int PANEL_BG = 0xB0000000;
    private static final int PHOON_AHEAD = 0xFFE060FF;
    private static final int PHOON_BEHIND = 0xFF8A50A0;
    private static final int YOU_AHEAD = 0xFF6BE06B;
    private static final int YOU_BEHIND = 0xFF4A8A4A;
    private static final int SEPARATOR = 0xFFBBBBBB;
    private static final int TIMER = 0xFFFFFFFF;
    private static final int TIMER_LOW = 0xFFE05050;
    private static final int TIRED = 0xFFFFD24A;

    private float phase;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!ClientBossState.active()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        Font font = minecraft.font;
        phase += deltaTracker.getGameTimeDeltaTicks();

        double tension = ClientBossState.tension();
        double amplitude = 1.2 + 2.6 * tension;

        int shakeX = (int) Math.round(Math.sin(phase * 1.7) * amplitude + Math.sin(phase * 4.3) * amplitude * 0.4);
        int shakeY = (int) Math.round(Math.cos(phase * 2.3) * amplitude * 0.7 + Math.cos(phase * 5.1) * amplitude * 0.3);

        long boss = ClientBossState.bossPoints();
        long you = ClientBossState.playerPoints();
        String name = minecraft.player.getGameProfile().name();

        String left = "PHOON | %,d".formatted(boss);
        String middle = "  VS.  ";
        String right = "%s | %,d".formatted(name, you);

        int leftWidth = font.width(left);
        int middleWidth = font.width(middle);
        int rightWidth = font.width(right);
        int totalWidth = leftWidth + middleWidth + rightWidth;

        int x = (graphics.guiWidth() - totalWidth) / 2 + shakeX;
        int y = 6 + shakeY;

        graphics.fill(x - 6, y - 4, x + totalWidth + 6, y + 13, PANEL_BG);

        graphics.text(font, left, x, y, boss >= you ? PHOON_AHEAD : PHOON_BEHIND, true);
        graphics.text(font, middle, x + leftWidth, y, SEPARATOR, true);
        graphics.text(font, right, x + leftWidth + middleWidth, y, you > boss ? YOU_AHEAD : YOU_BEHIND, true);

        int seconds = ClientBossState.ticksLeft() / 20;
        String clock = "%d:%02d".formatted(seconds / 60, seconds % 60);
        graphics.text(
                font,
                clock,
                (graphics.guiWidth() - font.width(clock)) / 2 + shakeX,
                y + 14,
                seconds <= 30 ? TIMER_LOW : TIMER,
                true);

        if (ClientBossState.tired()) {
            String tired = "PHOON IS TIRED — HIT IT";
            int tiredX = (graphics.guiWidth() - font.width(tired)) / 2 + shakeX * 2;
            graphics.fill(tiredX - 5, y + 24, tiredX + font.width(tired) + 5, y + 37, PANEL_BG);
            graphics.text(font, tired, tiredX, y + 27, TIRED, true);
        }
    }
}

