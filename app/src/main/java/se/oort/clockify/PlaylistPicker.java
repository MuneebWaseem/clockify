package se.oort.clockify;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class PlaylistPicker extends ListActivity {

    private static final String LOG_TAG = SpotifyProxy.ROOT_LOG_TAG + "/PlaylistPicker";
    private SpotifyProxy spotify = SpotifyProxy.getInstance();
    private ArrayAdapter<PlaylistWrapper> listAdapter;

    private static class PlaylistWrapper {
        protected PlaylistSimple backend;
        public PlaylistWrapper(PlaylistSimple be) {
            this.backend = be;
        }
        public static List<PlaylistWrapper> convert(List<PlaylistSimple> list) {
            List<PlaylistWrapper> result = new ArrayList<PlaylistWrapper>();
            for (PlaylistSimple playlist : list) {
                result.add(new PlaylistWrapper(playlist));
            }
            return result;
        }
        public String toString() {
            return backend.name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        listAdapter = new ArrayAdapter<PlaylistWrapper>(this, R.layout.playlist_item, R.id.textView, new ArrayList<PlaylistWrapper>()) {
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
                    convertView = inflater.inflate(R.layout.playlist_item, parent, false);
                }

                TextView text = (TextView) convertView.findViewById(R.id.textView);
                Button button = (Button) convertView.findViewById(R.id.button);

                text.setText(this.getItem(position).toString());
                text.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PlaylistPicker.this.playlistSelected(position);
                    }
                });

                button.setOnTouchListener(new View.OnTouchListener() {
                    private boolean playing = false;
                    private ProgressDialog progressDialog = new ProgressDialog(PlaylistPicker.this);
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN && !playing) {
                            spotify.play(PlaylistPicker.this, getItem(position).backend.uri, new SpotifyProxy.ResponseHandler<String>() {
                                @Override
                                public void handle(String s) {
                                    Log.d(LOG_TAG, s);
                                }

                                @Override
                                public void error(String s) {
                                    Toast.makeText(PlaylistPicker.this, s, Toast.LENGTH_LONG);
                                }
                            });
                            progressDialog.setMessage("Playing " + getItem(position).backend.name);
                            progressDialog.show();
                            playing = true;
                        } else if (event.getAction() == MotionEvent.ACTION_UP && playing) {
                            Log.d(LOG_TAG, "Pause");
                            spotify.pause(PlaylistPicker.this);
                            playing = false;
                            progressDialog.dismiss();
                        }
                        return false;
                    }
                });

                return convertView;
            }
        };
        setListAdapter(listAdapter);

        spotify.getPlaylists(this, new SpotifyProxy.ResponseHandler<List<PlaylistSimple>>() {
            @Override
            public void handle(List<PlaylistSimple> list) {
                listAdapter.addAll(PlaylistWrapper.convert(list));
                listAdapter.notifyDataSetChanged();
            }
            @Override
            public void error(String s) {
                Toast.makeText(PlaylistPicker.this, s, Toast.LENGTH_LONG);
            }
        });
    }

    private void playlistSelected(int position) {
        Intent intent = new Intent();

        PlaylistSimple playlist = listAdapter.getItem(position).backend;
        Uri uri = Uri.parse(playlist.uri + "/" + Uri.encode(playlist.name));

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
        setResult(RESULT_OK, intent);
        finish();
    }


}
