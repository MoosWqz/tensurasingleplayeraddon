package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.AttachmentRegistry;
import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.server.level.ServerPlayer;

/**
 * Completes the player-context metadata that could not exist in version-1
 * recognition saves.
 *
 * <p>The operation is intentionally narrow and idempotent. It never changes
 * committed paths, purity, title, scores, balance provenance, rule versions,
 * reward versions or the contradiction modifier. Existing non-blank metadata
 * is preserved by RecognitionData.</p>
 */
public final class RecognitionCommittedMetadataService {

    private static final String LEGACY_INCARNATION_PREFIX =
            "legacy-v1-";

    private RecognitionCommittedMetadataService() {
    }

    public static CompletionResult complete(
            ServerPlayer player
    ) {
        if (player == null) {
            return CompletionResult.INVALID_PLAYER;
        }

        RecognitionData data =
                player.getData(
                        AttachmentRegistry.RECOGNITION_DATA
                );

        if (!data.isNamingCommitted()) {
            return CompletionResult.NOT_COMMITTED;
        }

        boolean displayNameMissing =
                data.getFrozenDisplayName()
                        .isBlank();

        boolean incarnationIdMissing =
                data.getIncarnationId()
                        .isBlank();

        if (!displayNameMissing
                && !incarnationIdMissing) {
            return CompletionResult.ALREADY_COMPLETE;
        }

        String frozenDisplayName =
                data.getFrozenDisplayName();

        if (frozenDisplayName.isBlank()) {
            frozenDisplayName =
                    resolveFrozenDisplayName(
                            player,
                            data
                    );
        }

        String incarnationId =
                data.getIncarnationId();

        if (incarnationId.isBlank()) {
            incarnationId =
                    createLegacyIncarnationId(
                            player
                    );
        }

        boolean changed =
                data.completeCommittedPlayerMetadata(
                        frozenDisplayName,
                        incarnationId
                );

        if (!data.getFrozenDisplayName().isBlank()
                && !data.getIncarnationId().isBlank()) {
            return changed
                    ? CompletionResult.COMPLETED
                    : CompletionResult.ALREADY_COMPLETE;
        }

        return CompletionResult.STILL_INCOMPLETE;
    }

    private static String resolveFrozenDisplayName(
            ServerPlayer player,
            RecognitionData data
    ) {
        String nativeName =
                getNativeName(
                        player
                );

        /*
         * Prefer the exact name already stored by Tensura. For an existing
         * recognized player this normally contains the account name and the
         * bestowed title exactly as displayed in-game.
         */
        if (!nativeName.isBlank()) {
            return nativeName;
        }

        /*
         * Defensive fallback for worlds where the Tensura existence storage
         * is temporarily unavailable during login.
         *
         * The committed title is read from the frozen version-1 result. It is
         * never selected again from the active datapack pool.
         */
        String accountName =
                player.getGameProfile()
                        .getName();

        String bestowedTitle =
                data.getBestowedTitle();

        if (bestowedTitle.isBlank()) {
            return accountName;
        }

        return accountName
                + " "
                + bestowedTitle;
    }

    private static String createLegacyIncarnationId(
            ServerPlayer player
    ) {
        /*
         * Version-1 saves had no incarnation identifier. The stable player
         * UUID therefore becomes the deterministic identity of that already
         * committed legacy incarnation.
         *
         * Future reincarnations use their own newly generated IDs through the
         * normal reset and commitment flow.
         */
        return LEGACY_INCARNATION_PREFIX
                + player.getUUID();
    }

    private static String getNativeName(
            ServerPlayer player
    ) {
        IExistence existence =
                TensuraStorages.getExistenceFrom(
                        player
                );

        if (existence == null) {
            return "";
        }

        String name =
                existence.getName();

        return name == null
                ? ""
                : name.trim();
    }

    public enum CompletionResult {
        INVALID_PLAYER,
        NOT_COMMITTED,
        ALREADY_COMPLETE,
        COMPLETED,
        STILL_INCOMPLETE
    }
}