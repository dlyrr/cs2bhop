package com.santi.cs2bhop.progress;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.santi.cs2bhop.config.BhopConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /bhop} — personal stats and the leaderboard. */
public final class BhopCommand {

    private BhopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bhop")
                .executes(ctx -> stats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx ->
                                        stats(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("top")
                        .executes(ctx -> leaderboard(ctx.getSource(), 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> leaderboard(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("leaderboard").executes(ctx -> leaderboard(ctx.getSource(), 10))));
    }

    private static int stats(CommandSourceStack source, ServerPlayer player) {
        BhopSaveData data = BhopSaveData.get(source.getServer());
        BhopConfig config = BhopConfig.get();
        PlayerProgress progress = data.progress(player.getUUID());
        int level = progress.level();

        source.sendSuccess(
                () -> Component.literal(player.getGameProfile().name() + " — level " + level + "/" + BhopLevels.MAX_LEVEL)
                        .withStyle(ChatFormatting.AQUA),
                false);

        source.sendSuccess(
                () -> Component.literal("  %,d points  (rank #%d of %d)"
                                .formatted(progress.points(), data.rankOf(player.getUUID()), data.trackedPlayers()))
                        .withStyle(ChatFormatting.GRAY),
                false);

        if (level < BhopLevels.MAX_LEVEL) {
            source.sendSuccess(
                    () -> Component.literal("  %,d points to level %d".formatted(
                                    BhopLevels.pointsToNext(progress.points()), level + 1))
                            .withStyle(ChatFormatting.DARK_GRAY),
                    false);
        }

        source.sendSuccess(
                () -> Component.literal("  run %.0f u/s, ceiling %.0f u/s"
                                .formatted(BhopLevels.runSpeed(level, config), BhopLevels.speedCap(level, config)))
                        .withStyle(ChatFormatting.DARK_AQUA),
                false);

        source.sendSuccess(
                () -> Component.literal("  %,d hops, best %.0f u/s, longest chain %d"
                                .formatted(progress.totalHops(), progress.bestSpeed(), progress.bestStreak()))
                        .withStyle(ChatFormatting.GRAY),
                false);

        if (progress.phoonUnlocked()) {
            source.sendSuccess(
                    () -> Component.literal("  Phoon Boots unlocked").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        return 1;
    }

    private static int leaderboard(CommandSourceStack source, int count) {
        BhopSaveData data = BhopSaveData.get(source.getServer());
        List<BhopSaveData.Entry> top = data.leaderboard(count);

        if (top.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("Nobody has bhopped yet.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Bhop leaderboard").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (int i = 0; i < top.size(); i++) {
            BhopSaveData.Entry entry = top.get(i);
            int rank = i + 1;
            ChatFormatting colour =
                    switch (rank) {
                        case 1 -> ChatFormatting.GOLD;
                        case 2 -> ChatFormatting.WHITE;
                        case 3 -> ChatFormatting.YELLOW;
                        default -> ChatFormatting.GRAY;
                    };

            source.sendSuccess(
                    () -> Component.literal("  %d. %s — lvl %d, %,d pts, best %.0f u/s"
                                    .formatted(
                                            rank,
                                            entry.name(),
                                            entry.progress().level(),
                                            entry.progress().points(),
                                            entry.progress().bestSpeed()))
                            .withStyle(colour),
                    false);
        }

        return top.size();
    }
}
