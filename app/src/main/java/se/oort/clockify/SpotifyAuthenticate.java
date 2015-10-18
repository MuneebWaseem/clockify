package se.oort.clockify;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
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

import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class SpotifyAuthenticate extends Activity {

    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/SpotifyAuthenticate";
    private static final String CLIENT_ID = "f38c5148ab234d2bb5b1ba78f49e7ded";
    private static final String REDIRECT_URI = "clockify://callback";

    public static final int REQUEST_CODE_CANCELED = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_spotify_authenticate);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.CODE,
                REDIRECT_URI);
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
                Uri uri = Uri.parse(url);
                if (url.startsWith(REDIRECT_URI)) {
                    Uri.Builder uriBuilder = new Uri.Builder().scheme("https").authority("accounts.spotify.com").appendPath("api/token");
                    Volley.
                    Log.d(LOG_TAG, "Got " + uri);
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
