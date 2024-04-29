/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.display;

import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import androidx.preference.SwitchPreference;
import android.provider.Settings;

import org.lineageos.settings.R;
import org.lineageos.settings.utils.FileUtils;

import java.io.File;

public class DcDimmingSettingsFragment extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private SwitchPreference mDcDimmingPreference;
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dimlayer_exposure";
    private static final String HBM = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm_enabled";
    private static final String HBM_KEY = "hbm";

    private File hbmFile;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dcdimming_settings, rootKey);
        mDcDimmingPreference = findPreference(DC_DIMMING_ENABLE_KEY);
        if (FileUtils.fileExists(DC_DIMMING_NODE)) {
            mDcDimmingPreference.setEnabled(true);
            mDcDimmingPreference.setOnPreferenceChangeListener(this);
        } else {
            mDcDimmingPreference.setSummary(R.string.dc_dimming_enable_summary_not_supported);
            mDcDimmingPreference.setEnabled(false);
        }
        hbmFile = new File(HBM);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (DC_DIMMING_ENABLE_KEY.equals(preference.getKey())) {
            boolean enabled = (boolean) newValue;
            FileUtils.writeLine(DC_DIMMING_NODE, enabled ? "1" : "0");
            if (enabled) {
                disableHBM();
            }
            updateHBMPreference(!enabled);
        }
        return true;
    }

    private void disableHBM() {
        // Disable HBM mode
        FileUtils.writeLine(HBM, "0");
        // Make HBM mode path read-only
        hbmFile.setReadOnly();
        // Update HBM mode UI tile
        updateHBMUI(false);
    }

    private void updateHBMUI(boolean enabled) {
        Intent intent = new Intent("org.lineageos.settings.hbm.UPDATE_TILE");
        intent.putExtra("enabled", enabled);
        getActivity().sendBroadcast(intent);
        // Update HBM preference UI
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        sharedPrefs.edit().putBoolean(HBM_KEY, enabled).apply();
        updateHBMPreference(enabled);
    }

    private void updateHBMPreference(boolean enabled) {
        // Update HBM preference UI
        if (mDcDimmingPreference != null) {
            mDcDimmingPreference.setChecked(enabled);
        }
    }
}
