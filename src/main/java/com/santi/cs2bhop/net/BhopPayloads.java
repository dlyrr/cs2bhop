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
