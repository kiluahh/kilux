package com.termux.shared.termux.shell.command.environment;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.util.HashMap;

/**
 * Environment for {@link TermuxConstants#TERMUX_API_PACKAGE_NAME} app.
 */
public class TermuxAPIShellEnvironment {

    /** Environment variable prefix for the Termux:API app. */
    public static final String TERMUX_API_APP_ENV_PREFIX = TermuxConstants.TERMUX_ENV_PREFIX_ROOT + "_API_APP__";

    /** Environment variable for the Termux:API app version. */
    public static final String ENV_TERMUX_API_APP__VERSION_NAME = TERMUX_API_APP_ENV_PREFIX + "VERSION_NAME";

    /** Get shell environment for Termux:API app (integrated into main app). */
    @Nullable
    public static HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext) {
        // API is now integrated — version is same as main app, no separate env needed
        return null;
    }

}
