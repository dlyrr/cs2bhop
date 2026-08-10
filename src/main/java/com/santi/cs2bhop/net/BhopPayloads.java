package com.santi.cs2bhop.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class BhopPayloads {

    private BhopPayloads() {}

    /**
     * Server tells the client what its progression currently allows. The client needs this because
     * the physics run there — it cannot enforce a level-scaled speed cap it does not know about.
     */
    public record ProgressSync(
            long points, int level, int streak, double runSpeed, double speedCap, double pointMultiplier, boolean inBhopBiome)
            implements CustomPacketPayload {

        public static final Type<ProgressSync> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("cs2bhop", "progress_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSync> CODEC =
                CustomPacketPayload.codec(ProgressSync::write, ProgressSync::new);

        private ProgressSync(RegistryFriendlyByteBuf buf) {
            this(
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeLong(points);
            buf.writeVarInt(level);
            buf.writeVarInt(streak);
            buf.writeDouble(runSpeed);
            buf.writeDouble(speedCap);
            buf.writeDouble(pointMultiplier);
            buf.writeBoolean(inBhopBiome);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Live boss-fight scoreboard. Sent only while a fight is running. */
    public record BossSync(boolean active, long bossPoints, long playerPoints, int ticksLeft, boolean tired)
            implements CustomPacketPayload {

        public static final Type<BossSync> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cs2bhop", "boss_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, BossSync> CODEC =
                CustomPacketPayload.codec(BossSync::write, BossSync::new);

        private BossSync(RegistryFriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeLong(bossPoints);
            buf.writeLong(playerPoints);
            buf.writeVarInt(Math.max(0, ticksLeft));
            buf.writeBoolean(tired);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client pressed the boot ability key. Everything about the result is decided server-side. */
    public record ReleaseShockwave() implements CustomPacketPayload {

        public static final Type<ReleaseShockwave> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("cs2bhop", "release_shockwave"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseShockwave> CODEC =
                StreamCodec.unit(new ReleaseShockwave());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

