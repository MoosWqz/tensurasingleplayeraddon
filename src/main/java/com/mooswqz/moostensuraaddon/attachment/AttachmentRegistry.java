package com.mooswqz.moostensuraaddon.attachment;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class AttachmentRegistry {

    public static final DeferredRegister<AttachmentType<?>>
            ATTACHMENT_TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            MoosTensuraAddon.MODID
    );

    public static final Supplier<AttachmentType<GrantedSkillData>>
            GRANTED_SKILL_DATA = ATTACHMENT_TYPES.register(
            "granted_skill_data",
            () -> AttachmentType
                    .builder(GrantedSkillData::new)
                    .serialize(GrantedSkillData.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<GranterProgressData>>
            GRANTER_PROGRESS_DATA = ATTACHMENT_TYPES.register(
            "granter_progress_data",
            () -> AttachmentType
                    .builder(GranterProgressData::new)
                    .serialize(GranterProgressData.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<BorrowedSkillData>>
            BORROWED_SKILL_DATA = ATTACHMENT_TYPES.register(
            "borrowed_skill_data",
            () -> AttachmentType
                    .builder(BorrowedSkillData::new)
                    .serialize(BorrowedSkillData.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<RecognitionData>>
            RECOGNITION_DATA = ATTACHMENT_TYPES.register(
            "recognition_data",
            () -> AttachmentType
                    .builder(RecognitionData::new)
                    .serialize(RecognitionData.CODEC)
                    .copyOnDeath()
                    .build()
    );

    /*
     * Stored on zombie villagers while they are converting.
     *
     * It should not copy on death because a dead zombie villager did not
     * complete its cure.
     */
    public static final Supplier<AttachmentType<CureAttributionData>>
            CURE_ATTRIBUTION_DATA = ATTACHMENT_TYPES.register(
            "cure_attribution_data",
            () -> AttachmentType
                    .builder(CureAttributionData::new)
                    .serialize(CureAttributionData.CODEC)
                    .build()
    );

    private AttachmentRegistry() {
    }
}