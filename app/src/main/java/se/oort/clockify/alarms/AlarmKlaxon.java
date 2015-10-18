/*
 * Copyright (C) 2013 The Android Open Source Project
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

package se.oort.clockify.alarms;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;

import se.oort.clockify.R;
import se.oort.clockify.SpotifyProxy;
import se.oort.clockify.provider.AlarmInstance;

import java.io.IOException;

/**
 * Manages playing ringtone and vibrating the device.
 */
public class AlarmKlaxon {
    private static final long[] sVibratePattern = new long[] { 500, 500 };

    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/AlarmKlaxon";

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    private static MediaPlayer sMediaPlayer = null;

    public static void stop(Context context) {
        Log.v(LOG_TAG, "AlarmKlaxon.stop()");

        SpotifyProxy.getInstance().pause(context);
        // Stop audio playing
        if (sMediaPlayer != null) {
            sMediaPlayer.stop();
            AudioManager audioManager = (AudioManager)
                    context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.abandonAudioFocus(null);
            sMediaPlayer.release();
            sMediaPlayer = null;
        }

        ((Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE)).cancel();
    }

    public static void start(final Context context, final AlarmInstance instance, final boolean inTelephoneCall) {

        Log.v(LOG_TAG, "AlarmKlaxon.start()");

        // Make sure we are stop before starting
        stop(context);

        Uri alarmNoise = instance.mRingtone;

        try {
            // Check if we are in a call. If we are, use the in-call alarm
            // resource at a low volume to not disrupt the call.
            if (inTelephoneCall) {
                AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                        (int) (IN_CALL_VOLUME * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),
                        0);
            }
            String[] parts = instance.mRingtone.toString().split("/");
            SpotifyProxy.getInstance().play(context, parts[0], new SpotifyProxy.ResponseHandler<String>() {
                @Override
                public void handle(String s) {
                    Log.d(LOG_TAG, "Started playing " + instance.mRingtone);
                }
                @Override
                public void error(String s) {
                    Log.e(LOG_TAG, "Error playing " + instance.mRingtone + ": " + s);
                    startFallbackAlarm(context, inTelephoneCall);
                }
            });
        } catch (Throwable ex) {
            Log.e(LOG_TAG, "Error playing " + instance.mRingtone + ": " + ex);
            startFallbackAlarm(context, inTelephoneCall);
        }

        if (instance.mVibrate) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(sVibratePattern, 0);
        }

    }

    private static void startFallbackAlarm(final Context context, boolean inPhoneCall) {
        try {
            sMediaPlayer = new MediaPlayer();
            sMediaPlayer.setOnErrorListener(new OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(LOG_TAG, "Error occurred while playing audio. Stopping AlarmKlaxon.");
                    AlarmKlaxon.stop(context);
                    return true;
                }
            });
            Uri alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            sMediaPlayer.setDataSource(context, alarmNoise);
            if (inPhoneCall) {
                sMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
            }
            sMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            sMediaPlayer.setLooping(true);
            sMediaPlayer.prepare();
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            audioManager.requestAudioFocus(null,
                    AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            sMediaPlayer.start();
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Error playing fallback alarm: " + t);
        }
    }

}
