#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
PATH = ROOT / "src/main/java/com/mooswqz/moostensuraaddon/event/AddonLifecycleEvents.java"

if not PATH.is_file():
    print(f"[FAIL] Missing: {PATH}")
    raise SystemExit(1)

source = PATH.read_text(encoding="utf-8")

old_imports = """import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
"""

new_imports = """import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
"""

old_constants = """    private static final ResourceLocation CHARACTER_RESET_SCROLL =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "character_reset_scroll"
            );
"""

new_constants = """    private static final ResourceLocation CHARACTER_RESET_SCROLL =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "character_reset_scroll"
            );

    private static final ResourceLocation REWIND_TIME_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    "tensura",
                    "rewind_time"
            );

    /*
     * Tensura's reset scroll performs its real action from releaseUsing(...),
     * so LivingEntityUseItemEvent.Finish is never the authoritative success
     * signal. Arm on Stop, then confirm success when Tensura re-awards its
     * rewind_time advancement after resetEverything(...) has completed.
     */
    private static final long CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS = 5L;

    private static final Map<UUID, Long>
            PENDING_CHARACTER_RESETS = new HashMap<>();
"""

old_handler = """    @SubscribeEvent
    public static void onFinishedUsingItem(
            LivingEntityUseItemEvent.Finish event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                event.getItem().getItem()
        );

        if (!CHARACTER_RESET_SCROLL.equals(itemId)) {
            return;
        }

        AddonPlayerDataResetService.resetForNewIncarnation(
                player,
                AddonPlayerDataResetService.ResetReason.CHARACTER_RESET
        );
    }
"""

new_handler = """    @SubscribeEvent
    public static void onStoppedUsingItem(
            LivingEntityUseItemEvent.Stop event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                event.getItem().getItem()
        );

        if (!CHARACTER_RESET_SCROLL.equals(itemId)) {
            return;
        }

        PENDING_CHARACTER_RESETS.put(
                player.getUUID(),
                player.serverLevel().getGameTime()
        );
    }

    @SubscribeEvent
    public static void onAdvancementEarned(
            AdvancementEvent.AdvancementEarnEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !REWIND_TIME_ADVANCEMENT.equals(
                event.getAdvancement().id()
        )) {
            return;
        }

        Long armedGameTime = PENDING_CHARACTER_RESETS.remove(
                player.getUUID()
        );

        if (armedGameTime == null) {
            return;
        }

        long currentGameTime = player.serverLevel().getGameTime();
        long age = currentGameTime - armedGameTime;

        if (age < 0L
                || age > CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS) {
            return;
        }

        AddonPlayerDataResetService.resetForNewIncarnation(
                player,
                AddonPlayerDataResetService.ResetReason.CHARACTER_RESET
        );
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Long armedGameTime = PENDING_CHARACTER_RESETS.get(
                player.getUUID()
        );

        if (armedGameTime == null) {
            return;
        }

        long currentGameTime = player.serverLevel().getGameTime();

        if (currentGameTime < armedGameTime
                || currentGameTime - armedGameTime
                > CHARACTER_RESET_CONFIRMATION_WINDOW_TICKS) {
            PENDING_CHARACTER_RESETS.remove(
                    player.getUUID()
            );
        }
    }
"""

old_logout = """        RecognitionProgressScreenService.clear(player.getUUID());
        SubordinateOverviewService.forget(player.getUUID());
"""

new_logout = """        PENDING_CHARACTER_RESETS.remove(player.getUUID());
        RecognitionProgressScreenService.clear(player.getUUID());
        SubordinateOverviewService.forget(player.getUUID());
"""

def replace_exact(current: str, old: str, new: str, label: str) -> str:
    if new in current:
        print(f"[OK] {label}: already applied")
        return current
    if old not in current:
        print(f"[FAIL] {label}: expected current-tree source was not found")
        raise SystemExit(1)
    print(f"[CHANGED] {label}")
    return current.replace(old, new, 1)

source = replace_exact(source, old_imports, new_imports, "imports")
source = replace_exact(source, old_constants, new_constants, "reset confirmation state")
source = replace_exact(source, old_handler, new_handler, "Character Reset event bridge")
source = replace_exact(source, old_logout, new_logout, "logout cleanup")

with PATH.open("w", encoding="utf-8", newline="\n") as handle:
    handle.write(source)

print("")
print("RESULT: PASS")
