package com.mooswqz.moostensuraaddon.skill;

public enum GranterMode {
    GRANT(0, "grant"),
    CHOOSE_SKILL(1, "choose_skill"),
    TAKE_BACK(2, "take_back"),
    LIST_SKILLS(3, "list");

    private final int id;
    private final String modeId;

    GranterMode(int id, String modeId) {
        this.id = id;
        this.modeId = modeId;
    }

    public int id() {
        return id;
    }

    public String modeId() {
        return modeId;
    }

    public static GranterMode fromId(int id) {
        for (GranterMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }

        return GRANT;
    }

    public static int count() {
        return values().length;
    }

    public GranterMode next(boolean reverse) {
        GranterMode[] values = values();
        int nextIndex = reverse ? this.ordinal() - 1 : this.ordinal() + 1;

        if (nextIndex < 0) {
            nextIndex = values.length - 1;
        }

        if (nextIndex >= values.length) {
            nextIndex = 0;
        }

        return values[nextIndex];
    }
}