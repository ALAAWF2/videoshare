package com.orange.videoplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.List;

public class IptvFavoritesActivity extends AppCompatActivity implements IptvFavoriteAdapter.Listener {

    private IptvStore store;
    private MaterialToolbar toolbar;
    private TextView emptyView;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iptv_favorites);

        store = new IptvStore(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyView = findViewById(R.id.empty);
        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<JSONObject> favorites = store.getFavorites();
        recycler.setAdapter(new IptvFavoriteAdapter(favorites, this));
        emptyView.setVisibility(favorites.isEmpty() ? View.VISIBLE : View.GONE);
        toolbar.setSubtitle(getString(R.string.iptv_favorites_count, favorites.size()));
    }

    @Override
    public void onFavoriteClick(JSONObject item) {
        if (item == null) return;
        String url = item.optString("direct_url");
        if (url == null || url.isEmpty()) url = item.optString("url");
        String name = item.optString("name");
        if (!url.isEmpty()) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("name", name);
            startActivity(intent);
        }
    }

    @Override
    public void onFavoriteDelete(JSONObject item) {
        if (item == null) return;
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.iptv_fav_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    store.removeFavorite(item.optString("url"));
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
