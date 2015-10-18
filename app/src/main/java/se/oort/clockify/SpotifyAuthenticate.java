package se.oort.clockify;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class SpotifyAuthenticate extends Activity {

    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/SpotifyAuthenticate";

    private StringRequest createTokenRequest(final String code, final Uri uri, final SpotifyProxy.ResponseHandler<String> handler) {
        return new StringRequest(Request.Method.POST, uri.toString(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(LOG_TAG, "Got response " + response);
                try {
                    JSONObject json = new JSONObject(response);
                    handler.handle(json.getString("refresh_token"));
                } catch (JSONException e) {
                    handler.error(e.toString());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                handler.error(error.toString());
            }
        }){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<String, String>();
                params.put("grant_type","authorization_code");
                params.put("code", code);
                params.put("redirect_uri",SpotifyProxy.REDIRECT_URI);
                Log.d(LOG_TAG, "Using params " + params);
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String,String> params = new HashMap<String, String>();
                params.put("Authorization", "Basic " + Base64.encodeToString((SpotifyProxy.CLIENT_ID + ":" + SpotifyProxy.CLIENT_SECRET).getBytes(), Base64.NO_WRAP));
                Log.d(LOG_TAG, "Using headers " + params);
                return params;
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_spotify_authenticate);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SpotifyProxy.CLIENT_ID,
                AuthenticationResponse.Type.CODE,
                SpotifyProxy.REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-read-private"});
        Uri uri = builder.build().toUri();

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Authenticating");

        final WebView webView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(false);
        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                Log.d(LOG_TAG, "Page finished");
                progressDialog.dismiss();
                webView.setVisibility(View.VISIBLE);
                super.onPageFinished(view, url);
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(LOG_TAG, "Page started");
                super.onPageStarted(view, url, favicon);
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                final Uri uri = Uri.parse(url);
                Log.d(LOG_TAG, "Got " + uri);
                if (url.startsWith(SpotifyProxy.REDIRECT_URI)) {
                    Uri tokenURI = new Uri.Builder().scheme("https").authority("accounts.spotify.com").appendPath("api").appendPath("token").build();
                    Log.d(LOG_TAG, "Going to POST to " + tokenURI);
                    Volley.newRequestQueue(SpotifyAuthenticate.this).add(createTokenRequest(uri.getQueryParameter("code"), tokenURI, new SpotifyProxy.ResponseHandler<String>() {
                        @Override
                        public void handle(String s) {
                            SpotifyProxy.getInstance().setRefreshToken(SpotifyAuthenticate.this, s, new SpotifyProxy.ResponseHandler<String>() {
                                @Override
                                public void handle(String s) {
                                    progressDialog.dismiss();
                                    SpotifyAuthenticate.this.finish();
                                }
                                public void error(String s) {
                                    Log.e(LOG_TAG, "Error setting refresh token: " + s);
                                    Toast.makeText(SpotifyAuthenticate.this, s, Toast.LENGTH_LONG);
                                }
                            });
                        }
                        @Override
                        public void error(String s) {
                            Log.e(LOG_TAG, "Error getting refresh token: " + s);
                            Toast.makeText(SpotifyAuthenticate.this, s, Toast.LENGTH_LONG);
                        }
                    }));
                    progressDialog.setMessage("Fetching refresh token");
                    progressDialog.show();
                    return true;
                } else if (uri.getAuthority().matches("^(.+\\.facebook\\.com)|(accounts\\.spotify\\.com)$")) {
                    return false;
                } else {
                    Intent launchBrowser = new Intent("android.intent.action.VIEW", uri);
                    SpotifyAuthenticate.this.startActivity(launchBrowser);
                    return true;
                }
            }

            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(LOG_TAG, "Got error " + error);
            }
        });
        progressDialog.show();
        webView.loadUrl(uri.toString());
    }
}
