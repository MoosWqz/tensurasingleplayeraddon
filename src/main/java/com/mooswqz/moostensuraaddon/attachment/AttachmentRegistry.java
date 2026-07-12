package com.mooswqz.moostensuraaddon.attachment;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class AttachmentRegistry {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MoosTensuraAddon.MODID);

    public static final Supplier<AttachmentType<GrantedSkillData>> GRANTED_SKILL_DATA =
            ATTACHMENT_TYPES.register("granted_skill_data", () ->
                    AttachmentType.<GrantedSkillData>builder(() -> new GrantedSkillData())
                            .serialize(GrantedSkillData.CODEC)
                            .build()
            );

    public static final Supplier<AttachmentType<GranterProgressData>> GRANTER_PROGRESS_DATA =
            ATTACHMENT_TYPES.register("granter_progress_data", () ->
                    AttachmentType.<GranterProgressData>builder(() -> new GranterProgressData())
                            .serialize(GranterProgressData.CODEC)
                            .build()
            );

    public static final Supplier<AttachmentType<BorrowedSkillData>> BORROWED_SKILL_DATA =
            ATTACHMENT_TYPES.register("borrowed_skill_data", () ->
                    AttachmentType.<BorrowedSkillData>builder(() -> new BorrowedSkillData())
                            .serialize(BorrowedSkillData.CODEC)
                            .build()
            );
}