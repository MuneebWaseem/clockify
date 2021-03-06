/*
 * Copyright (C) 2007 The Android Open Source Project
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

package se.oort.clockify;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

import se.oort.clockify.alarms.AlarmStateManager;

import se.oort.clockify.provider.AlarmInstance;
import se.oort.clockify.timer.TimerObj;

public class AlarmInitReceiver extends BroadcastReceiver {

    // A flag that indicates that switching the volume button default was done
    private static final String PREF_VOLUME_DEF_DONE = "vol_def_done";
    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/AlarmInitReceiver";

    /**
     * Sets alarm on ACTION_BOOT_COMPLETED.  Resets alarm on
     * TIME_SET, TIMEZONE_CHANGED
     */
    @Override
    public void onReceive(final Context context, Intent intent) {
        final String action = intent.getAction();
        Log.v(LOG_TAG, "AlarmInitReceiver " + action);

        final PendingResult result = goAsync();
        final WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(context);
        wl.acquire();
        AsyncHandler.post(new Runnable() {
            @Override public void run() {
                // Remove the snooze alarm after a boot.
                if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                    // Clear stopwatch and timers data
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(context);
                    Log.v(LOG_TAG, "AlarmInitReceiver - Reset timers and clear stopwatch data");
                    TimerObj.resetTimersInSharedPrefs(prefs);
                    Utils.clearSwSharedPref(prefs);

                    if (!prefs.getBoolean(PREF_VOLUME_DEF_DONE, false)) {
                        // Fix the default
                        Log.v(LOG_TAG, "AlarmInitReceiver - resetting volume button default");
                        switchVolumeButtonDefault(prefs);
                    }
                }

                // Register all instances after major time changes or phone restarts
                ContentResolver contentResolver = context.getContentResolver();
                for (AlarmInstance instance : AlarmInstance.getInstances(contentResolver, null)) {
                    AlarmStateManager.registerInstance(context, instance, false);
                }
                AlarmStateManager.updateNextAlarm(context);

                result.finish();
                Log.v(LOG_TAG, "AlarmInitReceiver finished");
                wl.release();
            }
        });
    }

    private void switchVolumeButtonDefault(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
            SettingsActivity.DEFAULT_VOLUME_BEHAVIOR);

        // Make sure we do it only once
        editor.putBoolean(PREF_VOLUME_DEF_DONE, true);
        editor.apply();
    }
}
