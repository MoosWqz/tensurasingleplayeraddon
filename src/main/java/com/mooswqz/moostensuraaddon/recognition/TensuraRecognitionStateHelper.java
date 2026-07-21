package com.mooswqz.moostensuraaddon.recognition;

import com.mooswqz.moostensuraaddon.attachment.RecognitionData;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper;
import com.mooswqz.moostensuraaddon.util.SkillCategoryHelper.SkillCategory;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TensuraRecognitionStateHelper {

    private static final Set<String> TRUE_HERO_BOOLEAN_METHODS = Set.of(
            "istruehero",
            "hastruehero",
            "isawakenedhero",
            "hasawakenedhero",
            "isheroawakened"
    );

    private static final Set<String> TRUE_DEMON_LORD_BOOLEAN_METHODS = Set.of(
            "istruedemonlord",
            "hastruedemonlord",
            "isawakeneddemonlord",
            "hasawakeneddemonlord",
            "isdemonlordawakened",
            "isharvestfestivalcomplete",
            "hascompletedharvestfestival"
    );

    private static final Set<String> DESCRIPTOR_METHOD_NAMES = Set.of(
            "getrace",
            "race",
            "getraceid",
            "getracename",
            "getregistryname",
            "getid",
            "getkey",
            "location",
            "getlocation",
            "getevolution",
            "getevolutionid",
            "getevolutionstage",
            "getawakening",
            "getawakeningstate",
            "getawakeningtype",
            "getstatus",
            "getstate",
            "getstage",
            "gettitle",
            "gettype",
            "getspecies"
    );

    private static final ConcurrentMap<Class<?>, List<Method>>
            INSPECTION_METHOD_CACHE = new ConcurrentHashMap<>();

    private static volatile List<Method> storageGetterCache;

    private TensuraRecognitionStateHelper() {
    }

    /**
     * Reads the current Tensura state and copies the relevant values into
     * the persistent recognition attachment.
     *
     * TH and TDL flags are intentionally one-way for the current incarnation.
     * Once detected, ordinary state changes cannot remove the modifier.
     */
    public static Snapshot synchronize(
            ServerPlayer player,
            RecognitionData data
    ) {
        if (player == null || data == null) {
            return Snapshot.empty();
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        double currentEp = existence == null
                ? 0.0D
                : sanitizeMeasurement(existence.getEP());

        data.setMeasurement(
                RecognitionStatKeys.CURRENT_EP,
                currentEp
        );

        data.setMeasurementMaximum(
                RecognitionStatKeys.HIGHEST_EP,
                currentEp
        );

        SkillSnapshot skillSnapshot = readSkillSnapshot(player);

        data.setCounter(
                RecognitionStatKeys.MASTERED_SKILLS,
                skillSnapshot.masteredSkills()
        );

        data.setCounter(
                RecognitionStatKeys.MASTERED_SKILL_CATEGORIES,
                skillSnapshot.masteredCategories()
        );

        AwakeningSnapshot awakeningSnapshot =
                inspectAwakeningState(player, existence);

        if (awakeningSnapshot.trueHero()) {
            data.setFlag(
                    RecognitionStatKeys.TRUE_HERO,
                    true
            );
        }

        if (awakeningSnapshot.trueDemonLord()) {
            data.setFlag(
                    RecognitionStatKeys.TRUE_DEMON_LORD,
                    true
            );
        }

        return new Snapshot(
                currentEp,
                skillSnapshot.masteredSkills(),
                skillSnapshot.masteredCategories(),
                awakeningSnapshot.trueHero(),
                awakeningSnapshot.trueDemonLord(),
                awakeningSnapshot.trueHeroSource(),
                awakeningSnapshot.trueDemonLordSource(),
                awakeningSnapshot.evidence()
        );
    }

    /**
     * Performs the same state inspection without requiring a RecognitionData
     * object. Used by the admin probe.
     */
    public static Snapshot inspect(ServerPlayer player) {
        if (player == null) {
            return Snapshot.empty();
        }

        IExistence existence = TensuraStorages.getExistenceFrom(player);

        double currentEp = existence == null
                ? 0.0D
                : sanitizeMeasurement(existence.getEP());

        SkillSnapshot skillSnapshot = readSkillSnapshot(player);

        AwakeningSnapshot awakeningSnapshot =
                inspectAwakeningState(player, existence);

        return new Snapshot(
                currentEp,
                skillSnapshot.masteredSkills(),
                skillSnapshot.masteredCategories(),
                awakeningSnapshot.trueHero(),
                awakeningSnapshot.trueDemonLord(),
                awakeningSnapshot.trueHeroSource(),
                awakeningSnapshot.trueDemonLordSource(),
                awakeningSnapshot.evidence()
        );
    }

    private static SkillSnapshot readSkillSnapshot(
            ServerPlayer player
    ) {
        Set<String> masteredSkillIds = new HashSet<>();
        EnumSet<SkillCategory> categories =
                EnumSet.noneOf(SkillCategory.class);

        try {
            for (ManasSkillInstance instance :
                    SkillAPI.getSkillsFrom(player).getLearnedSkills()) {

                if (instance == null) {
                    continue;
                }

                boolean mastered;

                try {
                    mastered = instance.isMastered(player);
                } catch (RuntimeException ignored) {
                    continue;
                }

                if (!mastered) {
                    continue;
                }

                ResourceLocation skillId = instance.getSkillId();

                if (SkillCategoryHelper.isIntrinsic(
                        instance,
                        skillId,
                        instance.getDisplayName()
                )) {
                    continue;
                }

                String stableSkillKey;

                if (skillId != null) {
                    stableSkillKey = skillId.toString();
                } else {
                    stableSkillKey = instance.getDisplayName()
                            .getString()
                            .trim()
                            .toLowerCase(Locale.ROOT);
                }

                if (stableSkillKey.isBlank()) {
                    continue;
                }

                masteredSkillIds.add(stableSkillKey);

                SkillCategory category =
                        SkillCategoryHelper.getCategory(
                                instance,
                                skillId,
                                instance.getDisplayName()
                        );

                if (category != null
                        && category != SkillCategory.OTHER) {
                    categories.add(category);
                }
            }
        } catch (RuntimeException ignored) {
            return new SkillSnapshot(0, 0);
        }

        return new SkillSnapshot(
                masteredSkillIds.size(),
                categories.size()
        );
    }

    private static AwakeningSnapshot inspectAwakeningState(
            ServerPlayer player,
            IExistence existence
    ) {
        List<InspectionTarget> targets =
                collectInspectionTargets(player, existence);

        ProbeBuilder probe = new ProbeBuilder();

        for (InspectionTarget target : targets) {
            inspectTarget(target, probe);
        }

        return probe.build();
    }

    private static List<InspectionTarget> collectInspectionTargets(
            ServerPlayer player,
            IExistence existence
    ) {
        List<InspectionTarget> result = new ArrayList<>();
        Set<Object> seenObjects =
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<>()
                );

        addTarget(
                result,
                seenObjects,
                "player",
                player
        );

        addTarget(
                result,
                seenObjects,
                "existence",
                existence
        );

        for (Method method : getStorageGetterMethods()) {
            try {
                Object value = method.invoke(null, player);
                value = unwrapOptional(value);

                addTarget(
                        result,
                        seenObjects,
                        "TensuraStorages." + method.getName(),
                        value
                );
            } catch (ReflectiveOperationException
                     | RuntimeException ignored) {
                // Optional compatibility probe. Failure is safe.
            }
        }

        return result;
    }

    private static void addTarget(
            List<InspectionTarget> targets,
            Set<Object> seenObjects,
            String label,
            Object value
    ) {
        Object unwrapped = unwrapOptional(value);

        if (unwrapped == null || seenObjects.contains(unwrapped)) {
            return;
        }

        seenObjects.add(unwrapped);
        targets.add(new InspectionTarget(label, unwrapped));
    }

    private static List<Method> getStorageGetterMethods() {
        List<Method> cached = storageGetterCache;

        if (cached != null) {
            return cached;
        }

        List<Method> discovered = new ArrayList<>();

        for (Method method : TensuraStorages.class.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            if (method.getParameterCount() != 1) {
                continue;
            }

            if (method.getReturnType() == Void.TYPE) {
                continue;
            }

            Class<?> parameterType =
                    method.getParameterTypes()[0];

            if (!parameterType.isAssignableFrom(
                    ServerPlayer.class
            )) {
                continue;
            }

            String normalizedName =
                    normalizeMethodName(method.getName());

            if (!isRelevantStorageGetter(normalizedName)) {
                continue;
            }

            discovered.add(method);
        }

        storageGetterCache = List.copyOf(discovered);
        return storageGetterCache;
    }

    private static boolean isRelevantStorageGetter(
            String normalizedName
    ) {
        if (!(normalizedName.startsWith("get")
                || normalizedName.startsWith("find")
                || normalizedName.startsWith("read"))) {
            return false;
        }

        return normalizedName.contains("race")
                || normalizedName.contains("existence")
                || normalizedName.contains("evolution")
                || normalizedName.contains("awakening")
                || normalizedName.contains("hero")
                || normalizedName.contains("demon")
                || normalizedName.contains("harvest");
    }

    private static void inspectTarget(
            InspectionTarget target,
            ProbeBuilder probe
    ) {
        Object value = target.value();

        inspectDescriptor(
                target.label() + ".class",
                value.getClass().getName(),
                probe
        );

        for (Method method : getInspectionMethods(
                value.getClass()
        )) {
            String normalizedName =
                    normalizeMethodName(method.getName());

            if (method.getReturnType() == Boolean.TYPE
                    || method.getReturnType() == Boolean.class) {
                inspectBooleanMethod(
                        target,
                        method,
                        normalizedName,
                        probe
                );
                continue;
            }

            if (!isDescriptorMethod(normalizedName)) {
                continue;
            }

            try {
                Object result = method.invoke(value);
                result = unwrapOptional(result);

                if (result == null) {
                    continue;
                }

                String descriptor =
                        convertDescriptorToString(result);

                inspectDescriptor(
                        target.label()
                                + "."
                                + method.getName(),
                        descriptor,
                        probe
                );

                inspectDescriptor(
                        target.label()
                                + "."
                                + method.getName()
                                + ".class",
                        result.getClass().getName(),
                        probe
                );
            } catch (ReflectiveOperationException
                     | RuntimeException ignored) {
                // Probe only. Unsupported methods are ignored safely.
            }
        }
    }

    private static void inspectBooleanMethod(
            InspectionTarget target,
            Method method,
            String normalizedName,
            ProbeBuilder probe
    ) {
        boolean heroCandidate =
                TRUE_HERO_BOOLEAN_METHODS.contains(normalizedName)
                        || isGenericTrueHeroBoolean(normalizedName);

        boolean demonLordCandidate =
                TRUE_DEMON_LORD_BOOLEAN_METHODS.contains(normalizedName)
                        || isGenericTrueDemonLordBoolean(normalizedName);

        if (!heroCandidate && !demonLordCandidate) {
            return;
        }

        try {
            Object result = method.invoke(target.value());

            if (!(result instanceof Boolean booleanResult)) {
                return;
            }

            String evidence =
                    target.label()
                            + "."
                            + method.getName()
                            + "="
                            + booleanResult;

            probe.addEvidence(evidence);

            if (!booleanResult) {
                return;
            }

            if (heroCandidate) {
                probe.detectTrueHero(evidence);
            }

            if (demonLordCandidate) {
                probe.detectTrueDemonLord(evidence);
            }
        } catch (ReflectiveOperationException
                 | RuntimeException ignored) {
            // Probe only.
        }
    }

    private static void inspectDescriptor(
            String source,
            String descriptor,
            ProbeBuilder probe
    ) {
        if (descriptor == null || descriptor.isBlank()) {
            return;
        }

        boolean trueHero = containsTrueHeroMarker(descriptor);
        boolean trueDemonLord =
                containsTrueDemonLordMarker(descriptor);

        if (!trueHero && !trueDemonLord) {
            return;
        }

        String evidence = source + "=" + descriptor;
        probe.addEvidence(evidence);

        if (trueHero) {
            probe.detectTrueHero(evidence);
        }

        if (trueDemonLord) {
            probe.detectTrueDemonLord(evidence);
        }
    }

    private static List<Method> getInspectionMethods(
            Class<?> targetClass
    ) {
        return INSPECTION_METHOD_CACHE.computeIfAbsent(
                targetClass,
                ignored -> {
                    List<Method> methods = new ArrayList<>();

                    for (Method method : targetClass.getMethods()) {
                        if (Modifier.isStatic(
                                method.getModifiers()
                        )) {
                            continue;
                        }

                        if (method.getParameterCount() != 0) {
                            continue;
                        }

                        String normalizedName =
                                normalizeMethodName(
                                        method.getName()
                                );

                        boolean relevantBoolean =
                                method.getReturnType()
                                        == Boolean.TYPE
                                        || method.getReturnType()
                                        == Boolean.class;

                        if (relevantBoolean
                                && (TRUE_HERO_BOOLEAN_METHODS.contains(
                                normalizedName
                        )
                                || TRUE_DEMON_LORD_BOOLEAN_METHODS.contains(
                                normalizedName
                        )
                                || isGenericTrueHeroBoolean(
                                normalizedName
                        )
                                || isGenericTrueDemonLordBoolean(
                                normalizedName
                        ))) {
                            methods.add(method);
                            continue;
                        }

                        if (isDescriptorMethod(normalizedName)) {
                            methods.add(method);
                        }
                    }

                    return List.copyOf(methods);
                }
        );
    }

    private static boolean isDescriptorMethod(
            String normalizedName
    ) {
        if (DESCRIPTOR_METHOD_NAMES.contains(normalizedName)) {
            return true;
        }

        return normalizedName.contains("race")
                || normalizedName.contains("awakening")
                || normalizedName.contains("evolution");
    }

    private static boolean isGenericTrueHeroBoolean(
            String normalizedName
    ) {
        if (!(normalizedName.startsWith("is")
                || normalizedName.startsWith("has"))) {
            return false;
        }

        return normalizedName.contains("truehero")
                || normalizedName.contains("awakenedhero")
                || (normalizedName.contains("hero")
                && normalizedName.contains("awaken"));
    }

    private static boolean isGenericTrueDemonLordBoolean(
            String normalizedName
    ) {
        if (!(normalizedName.startsWith("is")
                || normalizedName.startsWith("has"))) {
            return false;
        }

        return normalizedName.contains("truedemonlord")
                || normalizedName.contains("awakeneddemonlord")
                || (normalizedName.contains("demonlord")
                && normalizedName.contains("awaken"))
                || normalizedName.contains("harvestfestivalcomplete");
    }

    private static boolean containsTrueHeroMarker(
            String rawValue
    ) {
        String normalized = normalizeDescriptor(rawValue);
        String compact = compactDescriptor(rawValue);

        return normalized.contains("true hero")
                || normalized.contains("awakened hero")
                || compact.contains("truehero")
                || compact.contains("awakenedhero");
    }

    private static boolean containsTrueDemonLordMarker(
            String rawValue
    ) {
        String normalized = normalizeDescriptor(rawValue);
        String compact = compactDescriptor(rawValue);

        return normalized.contains("true demon lord")
                || normalized.contains("awakened demon lord")
                || compact.contains("truedemonlord")
                || compact.contains("awakeneddemonlord");
    }

    private static String convertDescriptorToString(
            Object value
    ) {
        if (value instanceof Component component) {
            return component.getString();
        }

        if (value instanceof ResourceLocation resourceLocation) {
            return resourceLocation.toString();
        }

        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }

        return String.valueOf(value);
    }

    private static Object unwrapOptional(Object value) {
        Object current = value;

        while (current instanceof Optional<?> optional) {
            current = optional.orElse(null);
        }

        return current;
    }

    private static String normalizeMethodName(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeDescriptor(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(':', ' ')
                .replace('/', ' ')
                .replace('.', ' ')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String compactDescriptor(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static double sanitizeMeasurement(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }

        return value;
    }

    private record SkillSnapshot(
            int masteredSkills,
            int masteredCategories
    ) {
    }

    private record InspectionTarget(
            String label,
            Object value
    ) {
    }

    private static final class ProbeBuilder {

        private boolean trueHero;
        private boolean trueDemonLord;

        private String trueHeroSource = "";
        private String trueDemonLordSource = "";

        private final LinkedHashSet<String> evidence =
                new LinkedHashSet<>();

        private void detectTrueHero(String source) {
            trueHero = true;

            if (trueHeroSource.isBlank()) {
                trueHeroSource = source;
            }
        }

        private void detectTrueDemonLord(String source) {
            trueDemonLord = true;

            if (trueDemonLordSource.isBlank()) {
                trueDemonLordSource = source;
            }
        }

        private void addEvidence(String value) {
            if (value == null
                    || value.isBlank()
                    || evidence.size() >= 24) {
                return;
            }

            evidence.add(value);
        }

        private AwakeningSnapshot build() {
            return new AwakeningSnapshot(
                    trueHero,
                    trueDemonLord,
                    trueHeroSource,
                    trueDemonLordSource,
                    List.copyOf(evidence)
            );
        }
    }

    private record AwakeningSnapshot(
            boolean trueHero,
            boolean trueDemonLord,
            String trueHeroSource,
            String trueDemonLordSource,
            List<String> evidence
    ) {
    }

    public record Snapshot(
            double currentEp,
            int masteredSkills,
            int masteredCategories,
            boolean detectedTrueHero,
            boolean detectedTrueDemonLord,
            String trueHeroSource,
            String trueDemonLordSource,
            List<String> evidence
    ) {

        public Snapshot {
            currentEp = sanitizeMeasurement(currentEp);
            masteredSkills = Math.max(0, masteredSkills);
            masteredCategories = Math.max(
                    0,
                    masteredCategories
            );

            trueHeroSource = trueHeroSource == null
                    ? ""
                    : trueHeroSource;

            trueDemonLordSource =
                    trueDemonLordSource == null
                            ? ""
                            : trueDemonLordSource;

            evidence = evidence == null
                    ? List.of()
                    : List.copyOf(evidence);
        }

        public static Snapshot empty() {
            return new Snapshot(
                    0.0D,
                    0,
                    0,
                    false,
                    false,
                    "",
                    "",
                    List.of()
            );
        }
    }
}