package se.oort.clockify;

import android.app.Activity;
import android.content.Intent;

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

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
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
    private UserPrivate me;

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;

    public void init(Activity activity) {
        this.activity = activity;

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(activity, REQUEST_CODE, request);
    }

    static final int maxPlaylistLimit = 50;

    private void getPlaylists(final Callback<List<PlaylistSimple>> cb, final int offset) {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put("offset", offset);
        options.put("limit", maxPlaylistLimit);
        spotify.getPlaylists(me.id, options, new Callback<Pager<PlaylistSimple>>() {
            public void success(Pager<PlaylistSimple> pager, Response response) {
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

    public void getPlaylists(Callback<List<PlaylistSimple>> cb) {
        getPlaylists(cb, 0);
    }

    public void play(String uri) {
        mPlayer.play(uri);
    }

    public void pause() {
        mPlayer.pause();
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            final AuthenticationResponse authResponse = AuthenticationClient.getResponse(resultCode, intent);
            if (authResponse.getType() == AuthenticationResponse.Type.TOKEN) {
                android.util.Log.d("Clockify", "Got auth response " + authResponse.getType());
                SpotifyApi wrapper = new SpotifyApi();
                wrapper.setAccessToken(authResponse.getAccessToken());
                spotify = wrapper.getService();
                spotify.getMe(new Callback<UserPrivate>() {
                    public void success(UserPrivate user, Response response) {
                        android.util.Log.d("Clockify", "Got me " + user);
                        me = user;
                        Config playerConfig = new Config(SpotifyProxy.this.activity, authResponse.getAccessToken(), CLIENT_ID);
                        mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                            @Override
                            public void onInitialized(Player player) {
                                mPlayer.addConnectionStateCallback(SpotifyProxy.this);
                                mPlayer.addPlayerNotificationCallback(SpotifyProxy.this);
                            }
                            @Override
                            public void onError(Throwable throwable) {
                                android.util.Log.e("SpotifyProxy", "Could not initialize player: " + throwable.getMessage());
                            }
                        });
                    }
                    public void failure(RetrofitError error) {
                        android.util.Log.d("Clockify", "Failure fetching me: " + error);
                    }
                });
            }
        }
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
