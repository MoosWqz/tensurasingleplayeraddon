package com.mooswqz.moostensuraaddon.util;

import com.mooswqz.moostensuraaddon.skill.GranterMode;

import java.util.ArrayList;
import java.util.List;

public final class AuthorityActionValidationHarness {

    private AuthorityActionValidationHarness() {
    }

    public static ValidationResult validate() {
        List<String> failures = new ArrayList<>();
        int checks = 0;

        checks += check(
                failures,
                GranterMode.count() == 2,
                "Granter exposes exactly two modes."
        );
        checks += check(
                failures,
                GranterMode.fromId(0) == GranterMode.GRANT,
                "Granter mode 0 remains Grant."
        );
        checks += check(
                failures,
                GranterMode.fromId(2) == GranterMode.TAKE_BACK,
                "Legacy Take Back mode normalises safely."
        );
        checks += check(
                failures,
                GranterMode.fromId(3) == GranterMode.GRANT,
                "Legacy List mode normalises to a valid mode."
        );
        checks += check(
                failures,
                AuthorityActionMode.GRANTER_GRANT.selectionLimit() == 1,
                "Basic Granter Grant is single-skill."
        );
        checks += check(
                failures,
                AuthorityActionMode.GRANTER_TAKE_BACK.selectionLimit() == 1,
                "Basic Granter Take Back is single-skill."
        );
        checks += check(
                failures,
                AuthorityActionMode.BENEVOLENT_BESTOW.selectionLimit() == 32,
                "Bestow supports bounded multi-selection."
        );
        checks += check(
                failures,
                AuthorityActionMode.GOVERNANCE_INVEST.selectionLimit() == 32,
                "Invest supports bounded multi-selection."
        );
        checks += check(
                failures,
                AuthorityActionMode.BENEVOLENT_TAKE_BACK
                        .supportsAllEligibleToggle(),
                "Ranged Take Back supports All Eligible."
        );
        checks += check(
                failures,
                AuthorityActionMode.GOVERNANCE_TAKE_BACK
                        .supportsAllEligibleToggle(),
                "Global Take Back supports All Eligible."
        );

        AuthorityActionPolicy.RequestAnalysis duplicate =
                AuthorityActionPolicy.analyseRequest(
                        AuthorityActionMode.BENEVOLENT_BESTOW,
                        List.of("test:a", "test:a")
                );
        checks += check(
                failures,
                duplicate.malformed() && duplicate.duplicateFound(),
                "Duplicate submissions are rejected."
        );

        AuthorityActionPolicy.RequestAnalysis singleOverLimit =
                AuthorityActionPolicy.analyseRequest(
                        AuthorityActionMode.GRANTER_GRANT,
                        List.of("test:a", "test:b")
                );
        checks += check(
                failures,
                singleOverLimit.overLimit(),
                "Single-skill actions reject multiple skill IDs."
        );

        List<String> thirtyThree = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            thirtyThree.add("test:skill_" + index);
        }
        AuthorityActionPolicy.RequestAnalysis multiOverLimit =
                AuthorityActionPolicy.analyseRequest(
                        AuthorityActionMode.BENEVOLENT_BESTOW,
                        thirtyThree
                );
        checks += check(
                failures,
                multiOverLimit.overLimit(),
                "Ultimate multi-selection rejects more than 32 skills."
        );

        checks += check(
                failures,
                AuthorityCostDefaults.GRANTER_GRANT == 50_000.0D,
                "Granter default cost is 50,000."
        );
        checks += check(
                failures,
                AuthorityCostDefaults.BENEVOLENT_DIRECT_BASE
                        < AuthorityCostDefaults.GRANTER_GRANT,
                "Mastered Bestow is cheaper than Granter."
        );
        checks += check(
                failures,
                AuthorityCostDefaults.GOVERNANCE_DIRECT_BASE
                        < AuthorityCostDefaults.GRANTER_GRANT,
                "Mastered Invest is cheaper than Granter."
        );
        checks += check(
                failures,
                AuthorityCostDefaults.SEIZE == 250_000.0D,
                "Seize retains its intentionally high cost."
        );
        checks += check(
                failures,
                AuthorityCostDefaults.migrateUntouchedDefault(
                        AuthorityCostDefaults.OLD_BORROW,
                        AuthorityCostDefaults.OLD_BORROW,
                        AuthorityCostDefaults.BORROW
                ) == AuthorityCostDefaults.BORROW,
                "Untouched Borrow default migrates."
        );
        checks += check(
                failures,
                AuthorityCostDefaults.migrateUntouchedDefault(
                        42_000.0D,
                        AuthorityCostDefaults.OLD_BORROW,
                        AuthorityCostDefaults.BORROW
                ) == 42_000.0D,
                "Custom Borrow value remains unchanged."
        );
        checks += check(
                failures,
                AuthorityActionPolicy.sumCosts(
                        List.of(20_000.0D, 95_000.0D)
                ) == 115_000.0D,
                "Mixed mastered/unmastered costs sum correctly."
        );

        return new ValidationResult(
                checks,
                checks - failures.size(),
                List.copyOf(failures)
        );
    }

    public static void main(String[] args) {
        ValidationResult result = validate();
        System.out.println(
                "Authority action validation: "
                        + result.passedChecks()
                        + " / "
                        + result.totalChecks()
        );

        for (String failure : result.failures()) {
            System.out.println("FAIL: " + failure);
        }

        if (!result.success()) {
            throw new IllegalStateException(
                    "Authority action validation failed."
            );
        }
    }

    private static int check(
            List<String> failures,
            boolean condition,
            String description
    ) {
        if (!condition) {
            failures.add(description);
        }
        return 1;
    }

    public record ValidationResult(
            int totalChecks,
            int passedChecks,
            List<String> failures
    ) {
        public ValidationResult {
            totalChecks = Math.max(0, totalChecks);
            passedChecks = Math.max(0, passedChecks);
            failures = failures == null
                    ? List.of()
                    : List.copyOf(failures);
        }

        public boolean success() {
            return failures.isEmpty()
                    && passedChecks == totalChecks;
        }
    }
}