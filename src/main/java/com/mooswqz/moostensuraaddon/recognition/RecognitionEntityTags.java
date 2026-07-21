package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Central tag keys used by the recognition deed trackers.
 *
 * <p>Classification priority is intentionally conservative:</p>
 *
 * <ol>
 *     <li>{@link #IGNORED} overrides every other recognition tag.</li>
 *     <li>{@link #CIVILIANS} identifies protected non-combatants.</li>
 *     <li>{@link #HOSTILE_TO_CIVILIANS} is only meaningful for entities that
 *     are neither ignored nor civilians.</li>
 *     <li>Boss and major-enemy tags describe combat significance and morality,
 *     while the event tracker still decides which single deed category wins.</li>
 * </ol>
 *
 * <p>All tag files are datapack-driven and therefore reload through the
 * normal Minecraft {@code /reload} pipeline.</p>
 */
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

    public static boolean isIgnored(EntityType<?> entityType) {
        return isInTag(
                entityType,
                IGNORED
        );
    }

    public static boolean isTaggedCivilian(
            EntityType<?> entityType
    ) {
        return !isIgnored(entityType)
                && isInTag(
                entityType,
                CIVILIANS
        );
    }

    public static boolean isTaggedHostileToCivilians(
            EntityType<?> entityType
    ) {
        return !isIgnored(entityType)
                && !isTaggedCivilian(entityType)
                && isInTag(
                entityType,
                HOSTILE_TO_CIVILIANS
        );
    }

    private static boolean isInTag(
            EntityType<?> entityType,
            TagKey<EntityType<?>> tag
    ) {
        return entityType != null
                && tag != null
                && entityType.is(tag);
    }

    private static TagKey<EntityType<?>> create(
            String path
    ) {
        return TagKey.create(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        MoosTensuraAddon.MODID,
                        path
                )
        );
    }
}