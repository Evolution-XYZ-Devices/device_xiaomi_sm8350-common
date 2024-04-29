package org.lineageos.settings.hbm;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.preference.PreferenceManager;
import android.provider.Settings;

import org.lineageos.settings.utils.FileUtils;
import org.lineageos.settings.display.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AutoHBMService extends Service {
    private static final String HBM = "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/hbm_enabled";
    private static final String BACKLIGHT = "/sys/class/backlight/panel0-backlight/brightness";

    private static boolean mAutoHBMActive = false;
    private ExecutorService mExecutorService;

    private SensorManager mSensorManager;
    Sensor mLightSensor;

    private SharedPreferences mSharedPrefs;
    private boolean dcDimmingEnabled;

    public void activateLightSensorRead() {
        submit(() -> {
            mSensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mSensorManager.registerListener(mSensorEventListener, mLightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        });
    }

    public void deactivateLightSensorRead() {
        submit(() -> {
            mSensorManager.unregisterListener(mSensorEventListener);
            mAutoHBMActive = false;
            enableHBM(false);
        });
    }

    private void enableHBM(boolean enable) {
        if (enable) {
            FileUtils.writeLine(HBM, "1");
            FileUtils.writeLine(BACKLIGHT, "2047");
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 255);
        } else {
            FileUtils.writeLine(HBM, "0");
        }
    }

    private boolean isCurrentlyEnabled() {
        return FileUtils.getFileValueAsBoolean(HBM, false);
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {

        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            KeyguardManager km =
                (KeyguardManager) getSystemService(getApplicationContext().KEYGUARD_SERVICE);
            boolean keyguardShowing = km.inKeyguardRestrictedInputMode();
            float luxThreshold = Float.parseFloat(mSharedPrefs.getString(HBMFragment.KEY_AUTO_HBM_THRESHOLD, "20000"));
            long timeToDisableHBM = Long.parseLong(mSharedPrefs.getString(HBMFragment.KEY_HBM_DISABLE_TIME, "1"));

            if (lux > luxThreshold) {
                if ((!mAutoHBMActive || !isCurrentlyEnabled()) && !keyguardShowing && !dcDimmingEnabled) {
                    mAutoHBMActive = true;
                    enableHBM(true);
                }
            }
            if (lux < luxThreshold) {
                if (mAutoHBMActive) {
                    mExecutorService.submit(() -> {
                        try {
                            Thread.sleep(timeToDisableHBM * 1000);
                        } catch (InterruptedException e) {
                        }
                        if (lux < luxThreshold) {
                            mAutoHBMActive = false;
                            enableHBM(false);
                        }
                    });
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                activateLightSensorRead();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                deactivateLightSensorRead();
            }
        }
    };

    @Override
    public void onCreate() {
        mExecutorService = Executors.newSingleThreadExecutor();
        IntentFilter screenStateFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isInteractive()) {
            activateLightSensorRead();
        }
    }

    private Future < ? > submit(Runnable runnable) {
        return mExecutorService.submit(runnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mScreenStateReceiver);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isInteractive()) {
            deactivateLightSensorRead();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
