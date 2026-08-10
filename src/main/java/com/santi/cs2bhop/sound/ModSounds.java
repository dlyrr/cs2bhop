package com.santi.cs2bhop.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {

    /** The legendary phoon song. Plays when the Phoon Boots go off. */
    public static SoundEvent PHOON;

    /** Fired when a shockwave is released. */
    public static SoundEvent SHOCKWAVE;

    private ModSounds() {}

    public static void register() {
        PHOON = register("phoon");
        SHOCKWAVE = register("shockwave");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("cs2bhop", name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
