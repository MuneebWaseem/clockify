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

/**
 * Created by zond on 10/7/15.
 */
public class SpotifyProxy
        implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String AUTH_TOKEN_PREFS = "se.oort.clockify.SpotifyProxy.PREFS";
    private static final String AUTH_TOKEN_KEY = "se.oort.clockify.SpotifyProxy.AUTH_TOKEN_KEY";
    private static final String USER_ID_KEY = "se.oort.clockify.SpotifyProxy.USER_ID_KEY";
    private static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    private static final String REDIRECT_URI = "clockify://callback";

    private static SpotifyProxy instance = new SpotifyProxy();

    private SpotifyProxy() {
    }

    public static SpotifyProxy getInstance() {
        return instance;
    }

    private Player mPlayer;
    private Activity activity;
    private SpotifyService spotify;
    private Semaphore initSemaphore = new Semaphore(1);
    private CountDownLatch initDoneLatch = new CountDownLatch(1);
    private String userId;

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;

    public void init(Activity activity) {
        android.util.Log.d("SpotifyProxy", "Initializing from activity");
        try {
            initSemaphore.acquire();
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (initDoneLatch.getCount() > 0) {
            android.util.Log.d("SpotifyProxy", "Starting init flow");
            this.activity = activity;

            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                    AuthenticationResponse.Type.TOKEN,
                    REDIRECT_URI);
            builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-read-private"});
            AuthenticationRequest request = builder.build();

            AuthenticationClient.openLoginActivity(activity, REQUEST_CODE, request);
        } else {
            android.util.Log.d("SpotifyProxy", "Already initialized");
            initSemaphore.release();
        }
    }

    private void init(Context context) {
        android.util.Log.d("SpotifyProxy", "Initializing from context");
        try {
            initSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (initDoneLatch.getCount() > 0) {
            android.util.Log.d("SpotifyProxy", "Starting init flow");
            SharedPreferences prefs = context.getSharedPreferences(AUTH_TOKEN_PREFS, 0);
            init(context, prefs.getString(AUTH_TOKEN_KEY, ""), prefs.getString(USER_ID_KEY, ""));
        } else {
            android.util.Log.d("SpotifyProxy", "Already initialized");
            initSemaphore.release();
        }
    }

    static final int maxPlaylistLimit = 50;

    private void awaitInitDone() {
        try {
            initDoneLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void getPlaylists(final Callback<List<PlaylistSimple>> cb, final int offset) {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put("offset", offset);
        options.put("limit", maxPlaylistLimit);
        spotify.getPlaylists(userId, options, new Callback<Pager<PlaylistSimple>>() {
            public void success(Pager<PlaylistSimple> pager, Response response) {
                android.util.Log.d("SpotifyProxy", "Got playlists");
                for (PlaylistSimple playlist : pager.items) {
                    android.util.Log.d("SpotifyProxy", playlist.name);
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

    public void getPlaylists(Context context, Callback<List<PlaylistSimple>> cb) {
        android.util.Log.d("SpotifyProxy", "Asked to fetch playlists");
        init(context);
        awaitInitDone();
        android.util.Log.d("SpotifyProxy", "Fetching");
        getPlaylists(cb, 0);
    }

    public void play(Context context, String uri) {
        android.util.Log.d("SpotifyProxy", "Asked to play " + uri);
        init(context);
        awaitInitDone();
        android.util.Log.d("SpotifyProxy", "Playing");
        mPlayer.play(uri);
    }

    public void pause(Context context) {
        android.util.Log.d("SpotifyProxy", "Asked to pause");
        init(context);
        awaitInitDone();
        android.util.Log.d("SpotifyProxy", "Pausing");
        mPlayer.pause();
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            final AuthenticationResponse authResponse = AuthenticationClient.getResponse(resultCode, intent);
            android.util.Log.d("Clockify", "Got auth response " + authResponse.getType());
            if (authResponse.getType() == AuthenticationResponse.Type.TOKEN) {

                SpotifyApi wrapper = new SpotifyApi();
                wrapper.setAccessToken(authResponse.getAccessToken());
                spotify = wrapper.getService();

                spotify.getMe(new Callback<UserPrivate>() {
                    public void success(UserPrivate user, Response response) {
                        android.util.Log.d("Clockify", "Got me " + user);
                        SharedPreferences prefs = activity.getSharedPreferences(AUTH_TOKEN_PREFS, 0);
                        prefs.
                                edit().
                                putString(AUTH_TOKEN_KEY, authResponse.getAccessToken()).
                                putString(USER_ID_KEY, user.id).
                                apply();
                        init(activity, authResponse.getAccessToken(), user.id);
                    }

                    public void failure(RetrofitError error) {
                        initSemaphore.release();
                        android.util.Log.d("Clockify", "Failure fetching me: " + error);
                    }
                });
            }
        }
    }

    private void init(Context context, String accessToken, String userId) {
        android.util.Log.d("SpotifyProxy", "Init with access token " + accessToken + " and userId " + userId);

        if (spotify == null) {
            SpotifyApi wrapper = new SpotifyApi();
            wrapper.setAccessToken(accessToken);
            spotify = wrapper.getService();
        }

        this.userId = userId;

        Config playerConfig = new Config(context, accessToken, CLIENT_ID);
        mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
            @Override
            public void onInitialized(Player player) {
                mPlayer.addConnectionStateCallback(SpotifyProxy.this);
                mPlayer.addPlayerNotificationCallback(SpotifyProxy.this);
            }

            @Override
            public void onError(Throwable throwable) {
                initSemaphore.release();
                android.util.Log.e("SpotifyProxy", "Could not initialize player: " + throwable.getMessage());
            }
        });

        activity = null;
        initDoneLatch.countDown();
        initSemaphore.release();
    }

    @Override
    public void onLoggedIn() {
        android.util.Log.d("MainActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        android.util.Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable error) {
        android.util.Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        android.util.Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        android.util.Log.d("MainActivity", "Received connection message: " + message);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        android.util.Log.d("MainActivity", "Playback event received: " + eventType.name());
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String errorDetails) {
        android.util.Log.d("MainActivity", "Playback error received: " + errorType.name());
    }

    public void destroy() {
        Spotify.destroyPlayer(this);
    }

}
