package com.kaori.browser.permission;

import android.app.Activity;
import android.content.SharedPreferences;

import com.kaori.browser.R;
import com.kaori.browser.unit.HelperUnit;
import com.kaori.browser.view.NinjaToast;

public final class SitePermissionManager {

    private SitePermissionManager() {
    }

    public static void syncRevokedPermissions(
            Activity activity,
            SharedPreferences preferences
    ) {
        if (preferences.getBoolean("sp_microphone", false)
                && !HelperUnit.checkPermissionsMic(activity)) {
            preferences.edit().putBoolean("sp_microphone", false).apply();
        }
        if (preferences.getBoolean("sp_camera", false)
                && !HelperUnit.checkPermissionsCam(activity)) {
            preferences.edit().putBoolean("sp_camera", false).apply();
        }
        if (preferences.getBoolean("sp_location", false)
                && !HelperUnit.checkPermissionsLoc(activity)) {
            preferences.edit().putBoolean("sp_location", false).apply();
        }
    }

    public static void setLocationEnabled(
            Activity activity,
            SharedPreferences preferences,
            boolean enabled
    ) {
        preferences.edit().putBoolean("sp_location", enabled).apply();
        if (!enabled) {
            return;
        }

        HelperUnit.grantPermissionsLoc(activity);
        if (!HelperUnit.checkPermissionsLoc(activity)) {
            showMissingPermission(activity, R.string.setting_title_location);
        }
    }

    public static void setCameraEnabled(
            Activity activity,
            SharedPreferences preferences,
            boolean enabled
    ) {
        preferences.edit().putBoolean("sp_camera", enabled).apply();
        if (!enabled) {
            return;
        }

        HelperUnit.grantPermissionsCam(activity);
        if (!HelperUnit.checkPermissionsCam(activity)) {
            showMissingPermission(activity, R.string.error_allow_camera);
        }
    }

    public static void setMicrophoneEnabled(
            Activity activity,
            SharedPreferences preferences,
            boolean enabled
    ) {
        preferences.edit().putBoolean("sp_microphone", enabled).apply();
        if (!enabled) {
            return;
        }

        HelperUnit.grantPermissionsMic(activity);
        if (!HelperUnit.checkPermissionsMic(activity)) {
            showMissingPermission(activity, R.string.error_allow_microphone);
        }
    }

    private static void showMissingPermission(Activity activity, int detailResId) {
        NinjaToast.show(
                activity,
                activity.getString(R.string.error_missing_permission)
                        + "\n"
                        + activity.getString(detailResId)
        );
    }
}
