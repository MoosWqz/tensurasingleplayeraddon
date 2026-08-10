package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class RecognitionEntityTags {

    public static final TagKey<EntityType<?>> CIVILIANS =
            create("recognition/civilians");

    public static final TagKey<EntityType<?>> HOSTILE_TO_CIVILIANS =
            create("recognition/hostile_to_civilians");

    public static final TagKey<EntityType<?>> BABY_KILL_MORALITY =
            create("recognition/baby_kill_morality");

    public static final TagKey<EntityType<?>> MAJOR_ENEMIES =
            create("recognition/major_enemies");

    public static final TagKey<EntityType<?>> MALEVOLENT_BOSSES =
            create("recognition/malevolent_bosses");

    public static final TagKey<EntityType<?>> BENEVOLENT_BOSSES =
            create("recognition/benevolent_bosses");

    public static final TagKey<EntityType<?>> IGNORED =
            create("recognition/ignored");

    private RecognitionEntityTags() {
    }

    private static TagKey<EntityType<?>> create(String path) {
        return TagKey.create(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        MoosTensuraAddon.MODID,
                        path
                )
        );
    }
}
