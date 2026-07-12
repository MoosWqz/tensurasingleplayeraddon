package com.mooswqz.moostensuraaddon.item;

import com.mooswqz.moostensuraaddon.MoosTensuraAddon;
import com.mooswqz.moostensuraaddon.block.BlockRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MoosTensuraAddon.MODID);

    public static final DeferredHolder<Item, Item> MOOSTENSURA =
            ITEMS.register("moostensura", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> GREAT_CRYSTAL_ALTAR =
            ITEMS.register("great_crystal_altar", () -> new BlockItem(
                    BlockRegistry.GREAT_CRYSTAL_ALTAR.get(),
                    new Item.Properties()
            ));

    public static final DeferredHolder<Item, SoulResonatorItem> SOUL_RESONATOR =
            ITEMS.register("soul_resonator", () -> new SoulResonatorItem(
                    new Item.Properties()
                            .stacksTo(1)
            ));

    private ItemRegistry() {
    }
}