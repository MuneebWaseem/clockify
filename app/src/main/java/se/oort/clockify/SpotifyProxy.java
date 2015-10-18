package se.oort.clockify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
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
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by zond on 10/7/15.
 */
public class SpotifyProxy
        implements PlayerNotificationCallback, ConnectionStateCallback {

    public interface ResponseHandler<T> {
        void handle(T t);
        void error(String s);
    }

    public static final String CLIENT_SECRET = "3b9f2795e7414b5f8f6fb23123f282f5";
    public static final String REDIRECT_URI = "clockify://callback";
    public static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    public static final String ROOT_LOG_TAG = "Clockify";

    private static final String PREFS_KEY = "se.oort.clockify.SpotifyProxy.PREFS_KEY";
    private static final String USER_ID_KEY = "se.oort.clockify.SpotifyProxy.USER_ID_KEY";
    private static final String REFRESH_TOKEN_KEY = "se.oort.clockify.SpotifyProxy.REFRESH_TOKEN_KEY";

    private static final int maxPlaylistLimit = 50;
    private static final SpotifyProxy instance = new SpotifyProxy();
    private static final String LOG_TAG = ROOT_LOG_TAG + "/SpotifyProxy";

    public static SpotifyProxy getInstance() {
        return instance;
    }

    private Player mPlayer;
    private Handler handler;
    private List<Runnable> callbacks = Collections.synchronizedList(new ArrayList<Runnable>());

    private SpotifyProxy() {
    }

    private SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE);
    }

    private String getUserId(Context context) {
        return getPrefs(context).getString(USER_ID_KEY, "");
    }

    private String getRefreshToken(Context context) {
        return getPrefs(context).getString(REFRESH_TOKEN_KEY, "");
    }

    private SpotifyService getSpotify(String authToken) {
        SpotifyApi api = new SpotifyApi();
        api.setAccessToken(authToken);
        return api.getService();
    }

    private ResponseHandler<String> withSpotify(final ResponseHandler<SpotifyService> handler) {
        return new ResponseHandler<String>() {
            @Override
            public void handle(String s) {
                handler.handle(getSpotify(s));
            }
            @Override
            public void error(String s) {
                handler.error(s);
            }
        };
    }

    public void setRefreshToken(Context context, final String token, final ResponseHandler<String> handler) {
        final SharedPreferences prefs = getPrefs(context);
        prefs.edit().putString(REFRESH_TOKEN_KEY, token).apply();
        Log.d(LOG_TAG, "Saved refresh token " + token);
        run(context, withSpotify(new ResponseHandler<SpotifyService>() {
            @Override
            public void handle(SpotifyService spotify) {
                spotify.getMe(new Callback<UserPrivate>() {
                    @Override
                    public void success(UserPrivate user, Response response) {
                        prefs.edit().putString(USER_ID_KEY, user.id).apply();
                        Log.d(LOG_TAG, "Saved me " + user.id);
                        handler.handle(user.id);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        handler.error(error.toString());
                    }
                });
            }
            @Override
            public void error(String s) {
                handler.error(s);
            }
        }));
    }

    private StringRequest createTokenRequest(final Context context, Uri uri, final ResponseHandler<String> handler) {
        return new StringRequest(Request.Method.POST, uri.toString(), new com.android.volley.Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(LOG_TAG, "Got response " + response);
                try {
                    JSONObject json = new JSONObject(response);
                    handler.handle(json.getString("access_token"));
                } catch (JSONException e) {
                    handler.error(e.toString());
                }
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                handler.error(error.toString());
            }
        }){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<String, String>();
                params.put("grant_type","refresh_token");
                params.put("refresh_token", getRefreshToken(context));
                Log.d(LOG_TAG, "Using params " + params);
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String,String> params = new HashMap<String, String>();
                params.put("Authorization", "Basic " + Base64.encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(), Base64.NO_WRAP));
                Log.d(LOG_TAG, "Using headers " + params);
                return params;
            }
        };
    }

    public void run(Context context, ResponseHandler<String> handler) {
        Uri tokenURI = new Uri.Builder().scheme("https").authority("accounts.spotify.com").appendPath("api").appendPath("token").build();
        Log.d(LOG_TAG, "Going to POST to " + tokenURI);
        Volley.newRequestQueue(context).add(createTokenRequest(context, tokenURI, handler));
    }

    private void getPlaylists(final Context context, final SpotifyService spotify, final ResponseHandler<List<PlaylistSimple>> handler, final List<PlaylistSimple> collector, final int offset) {
        final Map<String, Object> options = new HashMap<String, Object>();
        options.put("offset", offset);
        options.put("limit", maxPlaylistLimit);
        spotify.getPlaylists(getUserId(context), options, new Callback<Pager<PlaylistSimple>>() {
            public void success(Pager<PlaylistSimple> pager, Response response) {
                for (PlaylistSimple playlist : pager.items) {
                    android.util.Log.d(LOG_TAG, playlist.name);
                }
                collector.addAll(pager.items);
                if (pager.next == null) {
                    handler.handle(collector);
                } else {
                    getPlaylists(context, spotify, handler, collector, offset + maxPlaylistLimit);
                }
            }

            public void failure(RetrofitError error) {
                handler.error(error.toString());
            }
        });
    }

    public void getPlaylists(final Context context, final ResponseHandler<List<PlaylistSimple>> handler) {
        run(context, withSpotify(new ResponseHandler<SpotifyService>() {
            @Override
            public void handle(SpotifyService spotify) {
                getPlaylists(context, spotify, handler, new ArrayList<PlaylistSimple>(), 0);
            }
            @Override
            public void error(String s) {
                handler.error(s);
            }
        }));
    }

    public void play(final Context context, final String uri, final ResponseHandler<String> handler) {
        Log.d(LOG_TAG, "Asked to play " + uri);
        run(context, new ResponseHandler<String>() {
            @Override
            public void handle(String token) {
                pause(context);
                Config playerConfig = new Config(context, token, CLIENT_ID);
                Log.d(LOG_TAG, "Creating player");
                mPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {
                    @Override
                    public void onInitialized(Player player) {
                        Log.d(LOG_TAG, "Playing");
                        mPlayer.addConnectionStateCallback(SpotifyProxy.this);
                        mPlayer.addPlayerNotificationCallback(SpotifyProxy.this);
                        mPlayer.play(uri);
                        mPlayer.setRepeat(true);
                        handler.handle(uri);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        handler.error(throwable.toString());
                    }
                });
            }
            @Override
            public void error(String s) {
                handler.error(s);
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
