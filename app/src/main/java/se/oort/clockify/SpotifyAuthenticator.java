package se.oort.clockify;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.UserPrivate;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import android.util.Log;

public class SpotifyAuthenticator extends Activity {

    private static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    private static final String REDIRECT_URI = "clockify://callback";
    private static final int REQUEST_CODE = 1337;
    private static SpotifyProxy spotify = SpotifyProxy.getInstance();
    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/SpotifyAuthenticator";
    private PowerManager.WakeLock wl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "Starting authenticator");
        super.onCreate(savedInstanceState);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.PARTIAL_WAKE_LOCK, "Clockify");
        wl.acquire();

        setContentView(R.layout.activity_spotify_init);
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-read-private"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        Log.d(LOG_TAG, "Authenticator launched login activity");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            final AuthenticationResponse authResponse = AuthenticationClient.getResponse(resultCode, intent);
            Log.d(LOG_TAG, "Got response " + authResponse.getType() + ", " + authResponse.getError());

            if (authResponse.getType() == AuthenticationResponse.Type.TOKEN) {
                spotify.setAccessToken(this, authResponse.getAccessToken());
                wl.release();
                finish();
            }
        }
    }
}
