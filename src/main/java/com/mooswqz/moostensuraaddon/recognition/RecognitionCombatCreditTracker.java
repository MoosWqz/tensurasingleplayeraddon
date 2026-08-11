package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.lifecycle.AddonIncarnationState;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatAttribution.CombatCredit;
import com.mooswqz.moostensuraaddon.recognition.RecognitionCombatAttribution.CombatCreditType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-isolated recent player damage and exactly-once death attribution.
 */
public final class RecognitionCombatCreditTracker {

    private static final String DEBUG_FIXTURE_DIMENSION =
            "moostensuraaddon:attribution_reset_fixture";

    private static final Map<MinecraftServer, RecognitionCombatCreditLedger>
            SERVER_LEDGERS = new WeakHashMap<>();

    private RecognitionCombatCreditTracker() {
    }

    public static void recordIncomingDamage(
            LivingEntity victim,
            DamageSource source
    ) {
        if (victim == null
                || source == null
                || !(victim.level()
                instanceof ServerLevel serverLevel)) {
            return;
        }

        Optional<CombatCredit> credit =
                RecognitionCombatAttribution.resolve(source);

        if (credit.isEmpty()) {
            return;
        }

        long gameTime = getServerGameTime(serverLevel);

        ledgerFor(serverLevel.getServer()).record(
                keyOf(victim),
                toLedgerCredit(credit.orElseThrow()),
                gameTime
        );
    }

    public static DeathResolution consumeDeath(
            LivingEntity victim,
            DamageSource source
    ) {
        if (victim == null
                || !(victim.level()
                instanceof ServerLevel serverLevel)) {
            return DeathResolution.noCredit(
                    DeathStatus.NO_CREDIT
            );
        }

        MinecraftServer server = serverLevel.getServer();
        Optional<RecognitionCombatCreditLedger.PlayerCredit> direct =
                RecognitionCombatAttribution.resolve(source)
                        .map(
                                RecognitionCombatCreditTracker
                                        ::toLedgerCredit
                        );

        RecognitionCombatCreditLedger.Resolution resolution =
                ledgerFor(server).consumeDeath(
                        keyOf(victim),
                        direct,
                        getServerGameTime(serverLevel),
                        playerUuid -> currentLifeToken(
                                server,
                                playerUuid
                        )
                );

        if (resolution.credit().isEmpty()) {
            return DeathResolution.noCredit(
                    toDeathStatus(resolution.status())
            );
        }

        RecognitionCombatCreditLedger.PlayerCredit stored =
                resolution.credit().orElseThrow();

        ServerPlayer player = server
                .getPlayerList()
                .getPlayer(stored.playerUuid());

        if (player == null) {
            return DeathResolution.noCredit(
                    DeathStatus.STALE_LIFE
            );
        }

        return new DeathResolution(
                toDeathStatus(resolution.status()),
                Optional.of(
                        new CombatCredit(
                                player,
                                toCombatCreditType(
                                        stored.actorKind()
                                )
                        )
                )
        );
    }

    public static void cleanup(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }

        RecognitionCombatCreditLedger ledger = existingLedger(server);

        if (ledger != null) {
            ledger.cleanup(server.overworld().getGameTime());
        }
    }

    public static void clearForPlayer(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        RecognitionCombatCreditLedger ledger = existingLedger(
                player.getServer()
        );

        if (ledger != null) {
            ledger.clearPlayer(player.getUUID());
        }
    }

    public static void clearServer(MinecraftServer server) {
        if (server == null) {
            return;
        }

        synchronized (SERVER_LEDGERS) {
            SERVER_LEDGERS.remove(server);
        }
    }

    public static void clearAll() {
        synchronized (SERVER_LEDGERS) {
            SERVER_LEDGERS.clear();
        }
    }

    public static RuntimeSnapshot inspect(
            MinecraftServer server,
            UUID selectedPlayer
    ) {
        RecognitionCombatCreditLedger ledger = existingLedger(server);

        if (ledger == null) {
            return new RuntimeSnapshot(
                    0,
                    0,
                    0,
                    RecognitionCombatCreditLedger.MAX_RECENT_CREDITS,
                    RecognitionCombatCreditLedger.MAX_PROCESSED_DEATHS,
                    serverLedgerCount()
            );
        }

        RecognitionCombatCreditLedger.Snapshot snapshot =
                ledger.inspect(selectedPlayer);

        return new RuntimeSnapshot(
                snapshot.recentCredits(),
                snapshot.processedDeaths(),
                snapshot.selectedPlayerCredits(),
                snapshot.maximumRecentCredits(),
                snapshot.maximumProcessedDeaths(),
                serverLedgerCount()
        );
    }

    /** Installs one synthetic credit used only by the reset fixture. */
    public static void installResetFixture(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        long gameTime = player.getServer().overworld() == null
                ? player.serverLevel().getGameTime()
                : player.getServer().overworld().getGameTime();

        ledgerFor(player.getServer()).record(
                new RecognitionCombatCreditLedger.VictimKey(
                        DEBUG_FIXTURE_DIMENSION,
                        UUID.randomUUID()
                ),
                new RecognitionCombatCreditLedger.PlayerCredit(
                        player.getUUID(),
                        AddonIncarnationState.load(player)
                                .getLifeToken(),
                        RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER
                ),
                gameTime
        );
    }

    public static long creditWindowTicks() {
        return RecognitionCombatCreditLedger.CREDIT_WINDOW_TICKS;
    }

    public static long duplicateWindowTicks() {
        return RecognitionCombatCreditLedger.DUPLICATE_WINDOW_TICKS;
    }

    private static RecognitionCombatCreditLedger.PlayerCredit
    toLedgerCredit(CombatCredit credit) {
        ServerPlayer player = credit.player();

        return new RecognitionCombatCreditLedger.PlayerCredit(
                player.getUUID(),
                AddonIncarnationState.load(player)
                        .getLifeToken(),
                toActorKind(credit.type())
        );
    }

    private static RecognitionAttributionPolicy.ActorKind toActorKind(
            CombatCreditType type
    ) {
        return switch (type) {
            case DIRECT_PLAYER ->
                    RecognitionAttributionPolicy.ActorKind.DIRECT_PLAYER;
            case PLAYER_PROJECTILE ->
                    RecognitionAttributionPolicy.ActorKind.PLAYER_PROJECTILE;
            case OWNED_COMPANION ->
                    RecognitionAttributionPolicy.ActorKind.OWNED_COMPANION;
            case TENSURA_SUBORDINATE ->
                    RecognitionAttributionPolicy.ActorKind.TENSURA_SUBORDINATE;
        };
    }

    private static CombatCreditType toCombatCreditType(
            RecognitionAttributionPolicy.ActorKind kind
    ) {
        return switch (kind) {
            case PLAYER_PROJECTILE ->
                    CombatCreditType.PLAYER_PROJECTILE;
            case OWNED_COMPANION ->
                    CombatCreditType.OWNED_COMPANION;
            case TENSURA_SUBORDINATE ->
                    CombatCreditType.TENSURA_SUBORDINATE;
            case NONE, DIRECT_PLAYER ->
                    CombatCreditType.DIRECT_PLAYER;
        };
    }

    private static DeathStatus toDeathStatus(
            RecognitionCombatCreditLedger.ResolutionStatus status
    ) {
        return switch (status) {
            case CREDITED -> DeathStatus.CREDITED;
            case NO_CREDIT -> DeathStatus.NO_CREDIT;
            case EXPIRED -> DeathStatus.EXPIRED;
            case STALE_LIFE -> DeathStatus.STALE_LIFE;
            case DUPLICATE_SUPPRESSED ->
                    DeathStatus.DUPLICATE_SUPPRESSED;
        };
    }

    private static String currentLifeToken(
            MinecraftServer server,
            UUID playerUuid
    ) {
        if (server == null || playerUuid == null) {
            return "";
        }

        ServerPlayer player = server
                .getPlayerList()
                .getPlayer(playerUuid);

        return player == null
                ? ""
                : AddonIncarnationState.load(player)
                .getLifeToken();
    }

    private static RecognitionCombatCreditLedger.VictimKey keyOf(
            LivingEntity victim
    ) {
        return new RecognitionCombatCreditLedger.VictimKey(
                victim.level()
                        .dimension()
                        .location()
                        .toString(),
                victim.getUUID()
        );
    }

    private static long getServerGameTime(
            ServerLevel level
    ) {
        return level.getServer().overworld() == null
                ? level.getGameTime()
                : level.getServer()
                .overworld()
                .getGameTime();
    }

    private static RecognitionCombatCreditLedger ledgerFor(
            MinecraftServer server
    ) {
        synchronized (SERVER_LEDGERS) {
            return SERVER_LEDGERS.computeIfAbsent(
                    server,
                    ignored -> new RecognitionCombatCreditLedger()
            );
        }
    }

    private static RecognitionCombatCreditLedger existingLedger(
            MinecraftServer server
    ) {
        if (server == null) {
            return null;
        }

        synchronized (SERVER_LEDGERS) {
            return SERVER_LEDGERS.get(server);
        }
    }

    private static int serverLedgerCount() {
        synchronized (SERVER_LEDGERS) {
            return SERVER_LEDGERS.size();
        }
    }

    public record DeathResolution(
            DeathStatus status,
            Optional<CombatCredit> credit
    ) {
        public DeathResolution {
            status = status == null
                    ? DeathStatus.NO_CREDIT
                    : status;
            credit = credit == null
                    ? Optional.empty()
                    : credit;
        }

        private static DeathResolution noCredit(
                DeathStatus status
        ) {
            return new DeathResolution(
                    status,
                    Optional.empty()
            );
        }

        public boolean duplicateSuppressed() {
            return status
                    == DeathStatus.DUPLICATE_SUPPRESSED;
        }
    }

    public enum DeathStatus {
        CREDITED,
        NO_CREDIT,
        EXPIRED,
        STALE_LIFE,
        DUPLICATE_SUPPRESSED
    }

    public record RuntimeSnapshot(
            int recentCredits,
            int processedDeaths,
            int selectedPlayerCredits,
            int maximumRecentCredits,
            int maximumProcessedDeaths,
            int trackedServerLedgers
    ) {
    }
}
