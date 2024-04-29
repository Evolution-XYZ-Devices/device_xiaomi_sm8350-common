/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2020 The LineageOS Project
 * Copyright (C) 2023 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.camera.NfcCameraService;
import org.lineageos.settings.dirac.DiracUtils;
import org.lineageos.settings.doze.DozeUtils;
import org.lineageos.settings.refreshrate.RefreshUtils;
import org.lineageos.settings.thermal.ThermalUtils;
import org.lineageos.settings.utils.FileUtils;

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "XiaomiParts-BCR";
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, "Received intent: " + intent.getAction());

        switch (intent.getAction()) {
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                onLockedBootCompleted(context);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                onBootCompleted(context);
                break;
        }
    }

    private static void onLockedBootCompleted(Context context) {
        // Services that don't require reading from data.
        NfcCameraService.startService(context);
    }

    private static void onBootCompleted(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Data is now accessible (user has just unlocked).
        try {
            DiracUtils.getInstance(context);
        } catch (Exception e) {
            Log.d(TAG, "Dirac is not present in system");
        }
        DozeUtils.onBootCompleted(context);
        FileUtils.enableService(context);
        RefreshUtils.initialize(context);
        ThermalUtils.startService(context);

        // DC Dimming
        boolean dcDimmingEnabled = sharedPrefs.getBoolean(DC_DIMMING_ENABLE_KEY, false);
        FileUtils.writeLine(DC_DIMMING_NODE, dcDimmingEnabled ? "1" : "0");
    }
}
