package com.mooswqz.moostensuraaddon;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.block.BlockRegistry;
import com.mooswqz.moostensuraaddon.client.ClientItemPropertyEvents;
import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import com.mooswqz.moostensuraaddon.item.ItemRegistry;
import com.mooswqz.moostensuraaddon.network.NetworkRegistry;
import com.mooswqz.moostensuraaddon.skill.SkillRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(MoosTensuraAddon.MODID)
public class MoosTensuraAddon {
    public static final String MODID = "moostensuraaddon";

    public MoosTensuraAddon(IEventBus modEventBus, ModContainer modContainer) {
        SkillRegistry.SKILLS.register(modEventBus);
        AttachmentRegistry.ATTACHMENT_TYPES.register(modEventBus);
        BlockRegistry.BLOCKS.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientItemPropertyEvents.register(modEventBus);
        }

        modEventBus.addListener(NetworkRegistry::registerPayloads);

        modContainer.registerConfig(ModConfig.Type.SERVER, MoosTensuraConfig.SPEC);
    }
}