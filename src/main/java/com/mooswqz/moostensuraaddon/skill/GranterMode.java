package com.mooswqz.moostensuraaddon.skill;

public enum GranterMode {

    GRANT(0, "grant"),
    TAKE_BACK(1, "take_back");

    private final int id;
    private final String modeId;

    GranterMode(
            int id,
            String modeId
    ) {
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
        return switch (id) {
            case 1, 2 -> TAKE_BACK;
            case 0, 3 -> GRANT;
            default -> GRANT;
        };
    }

    public static int count() {
        return values().length;
    }

    public GranterMode next(boolean reverse) {
        return switch (this) {
            case GRANT -> TAKE_BACK;
            case TAKE_BACK -> GRANT;
        };
    }
}