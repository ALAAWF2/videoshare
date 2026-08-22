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

public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.Listener {

    private HistoryStore store;
    private MaterialToolbar toolbar;
    private TextView emptyView;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsStore settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        store = HistoryStore.getInstance(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.getMenu().clear();
        toolbar.getMenu().add(0, 101, 0, R.string.history_clear_all)
                .setIcon(R.drawable.ic_delete)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 101) {
                confirmClearHistory();
                return true;
            }
            return false;
        });

        emptyView = findViewById(R.id.tv_empty);
        recycler = findViewById(R.id.recycler_history);
        recycler.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<JSONObject> items = store.getAll();
        recycler.setAdapter(new HistoryAdapter(items, this));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        toolbar.setSubtitle(getString(R.string.history_count, items.size()));
    }

    private void confirmClearHistory() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_clear_all)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.clearAll();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onHistoryClick(JSONObject item) {
        if (item == null) return;
        String url = item.optString("url");
        String title = item.optString("title");
        long pos = item.optLong("pos", -1);

        if (url != null && !url.isEmpty()) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("name", title);
            if (pos > 0) {
                intent.putExtra("pos", pos);
            }
            startActivity(intent);
        }
    }

    @Override
    public void onHistoryDelete(JSONObject item) {
        if (item == null) return;
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.history_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    store.delete(item.optLong("id"));
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
