package se.oort.clockify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.UserPrivate;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

import android.os.Handler;
import android.util.Log;

/**
 * Created by zond on 10/7/15.
 */
public class SpotifyProxy
        implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String PREFS_KEY = "se.oort.clockify.SpotifyProxy.PREFS_KEY";
    private static final String USER_ID_KEY = "se.oort.clockify.SpotifyProxy.USER_ID_KEY";
    private static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    private static final int maxPlaylistLimit = 50;
    private static final SpotifyProxy instance = new SpotifyProxy();
    private static final int REQUEST_CODE = 1337;

    public static final String ROOT_LOG_TAG = "Clockify";
    public static final String LOG_TAG = ROOT_LOG_TAG + "/SpotifyProxy";

    public static SpotifyProxy getInstance() {
        return instance;
    }

    private Player mPlayer;
    private SpotifyService spotify;
    private String userId;
    private String accessToken;
    private Handler handler;
    private List<Runnable> callbacks = Collections.synchronizedList(new ArrayList<Runnable>());

    private SpotifyProxy() {
    }

    private synchronized void init(Context context, Runnable callback) {
        Log.d(LOG_TAG, "Initializing");

        if (handler == null) {
            handler = new Handler();
        }
        callbacks.add(callback);
        Intent intent = new Intent(context, SpotifyAuthenticator.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        Log.d(LOG_TAG, "Going to launch " + intent);
        context.startActivity(intent);
        Log.d(LOG_TAG, "Launched");
    }

    public void setAccessToken(final Context context, String token) {
        Log.d(LOG_TAG, "Got access token " + token);

        accessToken = token;
        SpotifyApi wrapper = new SpotifyApi();
        wrapper.setAccessToken(accessToken);
        spotify = wrapper.getService();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE);
        userId = prefs.getString(USER_ID_KEY, "");
        if (userId.equals("")) {
            Log.d(LOG_TAG, "Getting me");
            spotify.getMe(new Callback<UserPrivate>() {

                public void success(UserPrivate user, Response response) {
                    Log.d(LOG_TAG, "Got me " + user.email + ", " + response);

                    userId = user.id;
                    context.getSharedPreferences(PREFS_KEY, 0).
                            edit().
                            putString(USER_ID_KEY, user.id).
                            apply();
                    Log.d(LOG_TAG, "Init done");
                    runCallbacks();
                }

                public void failure(RetrofitError error) {
                    Log.d(LOG_TAG, "Failure fetching me: " + error);
                }
            });
        } else {
            Log.d(LOG_TAG, "Init done");
            runCallbacks();
        }
    }

    private void runCallbacks() {
        synchronized(callbacks) {
            for (Runnable callback : callbacks) {
                handler.post(callback);
            }
            callbacks = Collections.synchronizedList(new ArrayList<Runnable>());
        }
    }

    private void getPlaylists(final Callback<List<PlaylistSimple>> cb, final int offset) {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put("offset", offset);
        options.put("limit", maxPlaylistLimit);
        spotify.getPlaylists(userId, options, new Callback<Pager<PlaylistSimple>>() {
            public void success(Pager<PlaylistSimple> pager, Response response) {
                for (PlaylistSimple playlist : pager.items) {
                    android.util.Log.d(LOG_TAG, playlist.name);
                }
                if (pager.next != null) {
                    getPlaylists(cb, offset + maxPlaylistLimit);
                }
                cb.success(pager.items, response);
            }

            public void failure(RetrofitError error) {
                cb.failure(error);
            }
        });
    }

    public void getPlaylists(Context context, final Callback<List<PlaylistSimple>> cb) {
        Log.d(LOG_TAG, "Asked to fetch playlists");
        init(context, new Runnable() {
            @Override
            public void run() {
                getPlaylists(cb, 0);
            }
        });
    }

    public void play(final Context context, final String uri) {
        Log.d(LOG_TAG, "Asked to play " + uri);
        init(context, new Runnable() {
            @Override
            public void run() {
                Config playerConfig = new Config(context, accessToken, CLIENT_ID);
                Log.d(LOG_TAG, "Creating player");
                mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        Log.d(LOG_TAG, "Playing");
                        mPlayer.addConnectionStateCallback(SpotifyProxy.this);
                        mPlayer.addPlayerNotificationCallback(SpotifyProxy.this);
                        mPlayer.play(uri);
                        mPlayer.setRepeat(true);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(LOG_TAG, "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        });

    }

    public void pause(Context context) {
        Log.d(LOG_TAG, "Asked to pause");
        if (mPlayer != null) {
            mPlayer.pause();
            Spotify.destroyPlayer(this);
        }
    }

    @Override
    public void onLoggedIn() {
        android.util.Log.d(LOG_TAG, "User logged in");
    }

    @Override
    public void onLoggedOut() {
        android.util.Log.d(LOG_TAG, "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        android.util.Log.d(LOG_TAG, "Login failed");
    }

    @Override
    public void onTemporaryError() {
        android.util.Log.d(LOG_TAG, "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        android.util.Log.d(LOG_TAG, "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        android.util.Log.d(LOG_TAG, "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        android.util.Log.d(LOG_TAG, "Playback error received: " + errorType.name());
    }

}
