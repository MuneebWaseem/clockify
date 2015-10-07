package se.oort.clockify;

import android.app.ListActivity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class PlaylistPicker extends ListActivity {

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
        listAdapter = new ArrayAdapter<PlaylistWrapper>(this, android.R.layout.simple_list_item_1, new ArrayList<PlaylistWrapper>());
        setListAdapter(listAdapter);
        spotify.getPlaylists(new Callback<List<PlaylistSimple>>() {
            public void success(List<PlaylistSimple> list, Response response) {
                listAdapter.addAll(PlaylistWrapper.convert(list));
                listAdapter.notifyDataSetChanged();
            }
            public void failure(RetrofitError error) {
                android.util.Log.d("Clockify", "Failure fetching playlists: " + error);
            }

        });
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // TODO Auto-generated method stub
        super.onListItemClick(l, v, position, id);

        Intent intent = new Intent();
        Bundle bundle = new Bundle();

        PlaylistSimple playlist = listAdapter.getItem(position).backend;
        Uri uri = Uri.parse(playlist.uri + "/" + Uri.encode(playlist.name));

        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri);
        setResult(RESULT_OK, intent);
        finish();
    }


}
