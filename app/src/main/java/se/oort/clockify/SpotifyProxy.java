package se.oort.clockify;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.*;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

/**
 * Created by zond on 10/7/15.
 */
public class SpotifyProxy
        implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    private static final String REDIRECT_URI = "clockify://callback";

    private Player mPlayer;
    private Activity activity;

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

    public void play(String uri) {
        mPlayer.play(uri);
    }

    public void pause() {
        mPlayer.pause();
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this.activity, response.getAccessToken(), CLIENT_ID);
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
