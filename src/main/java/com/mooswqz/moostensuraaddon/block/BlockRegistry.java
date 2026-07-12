package com.mooswqz.moostensuraaddon.block;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MoosTensuraAddon.MODID);

    public static final DeferredHolder<Block, GreatCrystalAltarBlock> GREAT_CRYSTAL_ALTAR =
            BLOCKS.register("great_crystal_altar", () -> new GreatCrystalAltarBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                            .strength(-1.0F, 3_600_000.0F)
                            .noOcclusion()
                            .lightLevel(state -> 9)
            ));

    private BlockRegistry() {
    }
}