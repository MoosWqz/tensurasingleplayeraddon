package com.mooswqz.moostensuraaddon.debug;

import com.mooswqz.moostensuraaddon.config.MoosTensuraConfig;
import net.minecraft.commands.CommandSourceStack;

public final class DebugModeService {

    public static final int STANDARD_DEBUG_PERMISSION_LEVEL =
            2;

    public static final int DANGEROUS_DEBUG_PERMISSION_LEVEL =
            4;

    private DebugModeService() {
    }

    public static boolean isEnabled() {
        return Boolean.TRUE.equals(
                MoosTensuraConfig.DEBUG_MODE.get()
        );
    }

    /**
     * Changes the authoritative server config and immediately saves it.
     *
     * The value therefore survives a server restart and is not controlled by
     * any connected client's local configuration.
     */
    public static boolean setEnabled(
            boolean enabled
    ) {
        boolean previousValue =
                isEnabled();

        if (previousValue == enabled) {
            return false;
        }

        MoosTensuraConfig.DEBUG_MODE.set(
                enabled
        );

        MoosTensuraConfig.SPEC.save();

        return true;
    }

    public static boolean canViewDebugStatus(
            CommandSourceStack source
    ) {
        return source != null
                && source.hasPermission(
                STANDARD_DEBUG_PERMISSION_LEVEL
        );
    }

    public static boolean canControlDebugMode(
            CommandSourceStack source
    ) {
        return source != null
                && source.hasPermission(
                DANGEROUS_DEBUG_PERMISSION_LEVEL
        );
    }

    public static boolean canUseDebugTools(
            CommandSourceStack source
    ) {
        return canViewDebugStatus(source)
                && isEnabled();
    }

    public static boolean canUseDangerousDebugTools(
            CommandSourceStack source
    ) {
        return canControlDebugMode(source)
                && isEnabled();
    }
}