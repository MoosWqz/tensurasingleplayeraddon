package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.command.PlayerGuidancePolicy;
import com.mooswqz.moostensuraaddon.recognition.RecognitionRewardNoticePolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic checks for guide-stage and notice lifecycle policy.
 */
public final class CoreGuidanceValidationHarness {

    private CoreGuidanceValidationHarness() {
    }

    public static Report validate() {
        List<Check> checks = new ArrayList<>();

        checkStage(
                checks,
                "reset guard wins",
                PlayerGuidancePolicy.Stage.INCARNATION_REBUILDING,
                true, true, true, true, true, true, true
        );
        checkStage(
                checks,
                "unformed identity",
                PlayerGuidancePolicy.Stage.FORM_IDENTITY,
                false, false, false, false, false, false, false
        );
        checkStage(
                checks,
                "named player seeks Sage",
                PlayerGuidancePolicy.Stage.SEEK_SAGE,
                false, true, false, false, false, false, false
        );
        checkStage(
                checks,
                "Sage seeks Great Sage",
                PlayerGuidancePolicy.Stage.AWAKEN_GREAT_SAGE,
                false, true, true, false, false, false, false
        );
        checkStage(
                checks,
                "Great Sage seeks Granter",
                PlayerGuidancePolicy.Stage.AWAKEN_GRANTER,
                false, true, true, true, false, false, false
        );
        checkStage(
                checks,
                "Granter seeks evolution",
                PlayerGuidancePolicy.Stage.EVOLVE_AUTHORITY,
                false, true, true, true, true, false, false
        );
        checkStage(
                checks,
                "Benevolent authority",
                PlayerGuidancePolicy.Stage.BENEVOLENT_EMPOWERMENT,
                false, true, true, true, false, true, false
        );
        checkStage(
                checks,
                "Governance authority",
                PlayerGuidancePolicy.Stage.ABSOLUTE_GOVERNANCE,
                false, true, true, true, false, false, true
        );

        String identity = RecognitionRewardNoticePolicy.createIdentity(
                "life-a",
                "fallback",
                2
        );
        checks.add(new Check(
                "notice identity uses recognition incarnation",
                identity.startsWith("life-a|")
        ));
        checks.add(new Check(
                "notice identity is stable",
                identity.equals(
                        RecognitionRewardNoticePolicy.createIdentity(
                                "life-a",
                                "fallback",
                                2
                        )
                )
        ));
        checks.add(new Check(
                "fallback lifecycle token",
                RecognitionRewardNoticePolicy.createIdentity(
                        "",
                        "life-b",
                        2
                ).startsWith("life-b|")
        ));
        checks.add(new Check(
                "missing incarnation rejected",
                RecognitionRewardNoticePolicy.createIdentity(
                        "",
                        "",
                        2
                ).isBlank()
        ));
        checks.add(new Check(
                "eligible notice shows",
                RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        identity,
                        ""
                )
        ));
        checks.add(new Check(
                "same notice does not repeat",
                !RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        identity,
                        identity
                )
        ));
        checks.add(new Check(
                "reset guard suppresses notice",
                !RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        false,
                        true,
                        true,
                        identity,
                        ""
                )
        ));
        checks.add(new Check(
                "native marker required",
                !RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        identity,
                        ""
                )
        ));
        checks.add(new Check(
                "future profile stays quiet",
                !RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        true,
                        false,
                        true,
                        identity,
                        ""
                )
        ));
        checks.add(new Check(
                "new incarnation can show",
                RecognitionRewardNoticePolicy.shouldShow(
                        true,
                        true,
                        true,
                        false,
                        false,
                        true,
                        RecognitionRewardNoticePolicy.createIdentity(
                                "life-c",
                                "",
                                2
                        ),
                        identity
                )
        ));

        return new Report(checks);
    }

    private static void checkStage(
            List<Check> checks,
            String name,
            PlayerGuidancePolicy.Stage expected,
            boolean reset,
            boolean identity,
            boolean sage,
            boolean greatSage,
            boolean granter,
            boolean benevolent,
            boolean governance
    ) {
        checks.add(new Check(
                name,
                PlayerGuidancePolicy.resolve(
                        reset,
                        identity,
                        sage,
                        greatSage,
                        granter,
                        benevolent,
                        governance
                ) == expected
        ));
    }

    public record Check(
            String name,
            boolean passed
    ) {
    }

    public record Report(
            List<Check> checks
    ) {
        public Report {
            checks = checks == null
                    ? List.of()
                    : List.copyOf(checks);
        }

        public long passedChecks() {
            return checks.stream().filter(Check::passed).count();
        }

        public long failedChecks() {
            return checks.size() - passedChecks();
        }

        public boolean passed() {
            return failedChecks() == 0L;
        }
    }
}