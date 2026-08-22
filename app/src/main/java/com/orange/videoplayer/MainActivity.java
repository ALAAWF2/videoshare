package com.orange.videoplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements LinkAdapter.Listener {

    private LinkStore store;
    private SettingsStore settingsStore;
    private TextView emptyView;
    private RecyclerView recycler;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new LinkStore(this);
        emptyView = findViewById(R.id.empty);
        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_local_media) {
                startActivity(new Intent(MainActivity.this, LocalMediaActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_social_download) {
                startActivity(new Intent(MainActivity.this, SocialDownloadActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_downloads) {
                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_history) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_iptv) {
                startActivity(new Intent(MainActivity.this, IptvActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    if (recycler != null && recycler.getAdapter() != null && recycler.getAdapter().getItemCount() > 0) {
                        recycler.smoothScrollToPosition(0);
                    }
                    return true;
                } else if (itemId == R.id.nav_iptv) {
                    startActivity(new Intent(MainActivity.this, IptvActivity.class));
                    return true;
                } else if (itemId == R.id.nav_local_media) {
                    startActivity(new Intent(MainActivity.this, LocalMediaActivity.class));
                    return true;
                } else if (itemId == R.id.nav_downloader) {
                    startActivity(new Intent(MainActivity.this, SocialDownloadActivity.class));
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                }
                return false;
            });
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddDialog(null));

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                String url = intent.getData().toString();
                saveAndPlay(LinkStore.autoName(url), url);
            }
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            String url = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
            if (url != null) showAddDialog(url);
        }
        setIntent(new Intent());
    }

    private void saveAndPlay(String name, String url) {
        Toast.makeText(this, R.string.resolving_direct, Toast.LENGTH_SHORT).show();
        IptvApiClient apiClient = new IptvApiClient();
        apiClient.resolveDirectUrl(url, new IptvApiClient.Callback<String>() {
            @Override
            public void onSuccess(String resolvedUrl) {
                String finalUrl = (resolvedUrl != null && !resolvedUrl.isEmpty()) ? resolvedUrl : url;
                long id = store.add(name, finalUrl);
                startPlayer(id, -1);
            }

            @Override
            public void onError(String error) {
                long id = store.add(name, url);
                startPlayer(id, -1);
            }
        });
    }

    private static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://[^\\s<>\"']+").matcher(text);
        return m.find() ? m.group() : null;
    }

    private void showAddDialog(String prefill) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_link, null);
        EditText etUrl = v.findViewById(R.id.et_url);
        EditText etName = v.findViewById(R.id.et_name);
        View btnPaste = v.findViewById(R.id.btn_paste);
        if (prefill != null) etUrl.setText(prefill);

        btnPaste.setOnClickListener(x -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                if (item.getText() != null) {
                    String raw = item.getText().toString().trim();
                    String extracted = extractUrl(raw);
                    etUrl.setText(extracted != null ? extracted : raw);
                }
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_link)
                .setView(v)
                .setPositiveButton(R.string.add, (d, w) -> {
                    String url = etUrl.getText().toString().trim();
                    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                        Toast.makeText(this, R.string.bad_url, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) name = LinkStore.autoName(url);
                    saveAndPlay(name, url);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onContinue(JSONObject entry) {
        startPlayer(entry.optLong("id"), -1);
    }

    @Override
    public void onRestart(JSONObject entry) {
        startPlayer(entry.optLong("id"), 0);
    }

    @Override
    public void onDelete(JSONObject entry) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.delete(entry.optLong("id"));
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onRename(JSONObject entry) {
        EditText et = new EditText(this);
        et.setText(entry.optString("name"));
        et.setPadding(48, 24, 48, 24);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rename)
                .setView(et)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) {
                        store.rename(entry.optLong("id"), n);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startPlayer(long id, long pos) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("id", id);
        if (pos >= 0) i.putExtra("pos", pos);
        startActivity(i);
    }

    private void refresh() {
        List<JSONObject> items = store.getAll();
        recycler.setAdapter(new LinkAdapter(items, this));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(getString(R.string.saved_count, items.size()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.getMenu().findItem(R.id.nav_home).setChecked(true);
        }
        refresh();
    }
}
