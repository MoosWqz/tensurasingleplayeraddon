package com.mooswqz.moostensuraaddon.recognition;

public record RecognitionNamingEligibility(
        Status status,
        int currentLevel,
        int requiredLevel,
        boolean nativeNamed,
        String nativeName,
        boolean recognitionCommitted,
        RecognitionEvaluation evaluation,
        RecognitionNamingCandidate candidate
) {

    public RecognitionNamingEligibility {
        status = status == null
                ? Status.NO_RECOGNITION_SELECTION
                : status;

        currentLevel = Math.max(0, currentLevel);
        requiredLevel = Math.max(0, requiredLevel);

        nativeName = nativeName == null
                ? ""
                : nativeName.trim();
    }

    public boolean eligible() {
        return status == Status.READY;
    }

    public boolean hasCandidate() {
        return candidate != null;
    }

    public enum Status {
        READY("ready"),
        ALREADY_COMMITTED("already_committed"),
        ALREADY_NAMED("already_named"),
        NOT_ENOUGH_LEVEL("not_enough_level"),
        NO_RECOGNITION_SELECTION("no_recognition_selection");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}