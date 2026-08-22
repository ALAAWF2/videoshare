package com.orange.videoplayer;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.List;

public class IptvActivity extends AppCompatActivity implements IptvSubscriptionAdapter.Listener {

    private IptvStore store;
    private IptvApiClient apiClient;

    private MaterialToolbar toolbar;
    private TextView emptyView;
    private RecyclerView recycler;
    private View cardFavorites;
    private TextView tvFavoritesSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsStore settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iptv);

        store = new IptvStore(this);
        apiClient = new IptvApiClient();

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_iptv);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_tunnel) {
                toggleTunnel();
                return true;
            }
            return false;
        });

        cardFavorites = findViewById(R.id.card_favorites);
        tvFavoritesSummary = findViewById(R.id.tv_favorites_summary);
        cardFavorites.setOnClickListener(v -> {
            startActivity(new Intent(this, IptvFavoritesActivity.class));
        });

        View cardHistory = findViewById(R.id.card_history);
        TextView tvHistorySummary = findViewById(R.id.tv_history_summary);
        cardHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        View cardDownloads = findViewById(R.id.card_downloads);
        TextView tvDownloadsSummary = findViewById(R.id.tv_downloads_summary);
        cardDownloads.setOnClickListener(v -> {
            startActivity(new Intent(this, DownloadsActivity.class));
        });

        emptyView = findViewById(R.id.empty);
        recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showAddDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<JSONObject> items = store.getAll();
        recycler.setAdapter(new IptvSubscriptionAdapter(items, this));
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        toolbar.setSubtitle(getString(R.string.iptv_subscriptions_count, items.size()));

        List<JSONObject> favs = store.getFavorites();
        tvFavoritesSummary.setText(getString(R.string.iptv_favorites_count, favs.size()));

        TextView tvHistorySummary = findViewById(R.id.tv_history_summary);
        if (tvHistorySummary != null) {
            int histCount = HistoryStore.getInstance(this).getAll().size();
            tvHistorySummary.setText(getString(R.string.history_count, histCount));
        }

        TextView tvDownloadsSummary = findViewById(R.id.tv_downloads_summary);
        if (tvDownloadsSummary != null) {
            int dlCount = DownloadStore.getInstance(this).getAll().size();
            tvDownloadsSummary.setText(getString(R.string.downloads_count, dlCount));
        }
    }

    private void showAddDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_iptv, null);

        RadioGroup rgType = v.findViewById(R.id.rg_type);
        RadioButton rbXtream = v.findViewById(R.id.rb_xtream);
        RadioButton rbM3u = v.findViewById(R.id.rb_m3u);

        EditText etName = v.findViewById(R.id.et_name);
        View containerXtream = v.findViewById(R.id.container_xtream);
        EditText etServer = v.findViewById(R.id.et_server);
        EditText etUsername = v.findViewById(R.id.et_username);
        EditText etPassword = v.findViewById(R.id.et_password);

        View containerM3u = v.findViewById(R.id.container_m3u);
        EditText etM3uUrl = v.findViewById(R.id.et_m3u_url);

        final boolean[] isPresetSelected = new boolean[]{false};

        // S Player (أكشن Tv) quick-fill preset
        v.findViewById(R.id.btn_splayer_preset).setOnClickListener(x -> {
            etName.setText(R.string.iptv_preset_name);
            etServer.setText("http://alico20.top");
            etUsername.requestFocus();
            isPresetSelected[0] = true;
        });

        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_xtream) {
                containerXtream.setVisibility(View.VISIBLE);
                containerM3u.setVisibility(View.GONE);
            } else {
                containerXtream.setVisibility(View.GONE);
                containerM3u.setVisibility(View.VISIBLE);
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.iptv_add_title)
                .setView(v)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (rbXtream.isChecked()) {
                        String server = etServer.getText().toString().trim();
                        String username = etUsername.getText().toString().trim();
                        String password = etPassword.getText().toString().trim();

                        if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
                            Toast.makeText(this, R.string.iptv_missing_fields, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<String> mirrors = new java.util.ArrayList<>();
                        if (isPresetSelected[0] || server.toLowerCase().contains("alico20") || server.toLowerCase().contains("tg7080")) {
                            mirrors.addAll(java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS));
                        }

                        verifyAndAddXtream(name, server, username, password, mirrors);
                    } else {
                        String url = etM3uUrl.getText().toString().trim();
                        if (url.isEmpty()) {
                            Toast.makeText(this, R.string.iptv_missing_fields, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        long id = store.addM3u(name, url);
                        refresh();
                        openSubscription(store.get(id));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void verifyAndAddXtream(String name, String server, String username, String password, List<String> mirrors) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.iptv_authenticating));
        progressDialog.setCancelable(false);
        progressDialog.show();

        apiClient.authenticateXtream(server, mirrors, username, password, new IptvApiClient.Callback<IptvApiClient.AuthResult>() {
            @Override
            public void onSuccess(IptvApiClient.AuthResult result) {
                if (!isFinishing()) {
                    progressDialog.dismiss();
                    String workingServer = (result != null && result.server != null) ? result.server : server;
                    long id = store.addXtream(name, workingServer, username, password, mirrors);
                    refresh();
                    openSubscription(store.get(id));
                }
            }

            @Override
            public void onError(String error) {
                if (!isFinishing()) {
                    progressDialog.dismiss();
                    boolean isAuthError = error != null && (error.contains("بيانات الاشتراك غير صحيحة") || error.contains("منتهية الصلاحية"));
                    if (isAuthError) {
                        new MaterialAlertDialogBuilder(IptvActivity.this)
                                .setTitle(R.string.iptv_add_title)
                                .setMessage(error + "\n\n" + getString(R.string.save_anyway_confirm))
                                .setPositiveButton(R.string.save, (d, w) -> {
                                    long id = store.addXtream(name, server, username, password, mirrors);
                                    refresh();
                                    openSubscription(store.get(id));
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        // Smart all-fail blocked dialog with retry, warp install, and save anyway option
                        IptvMirrorCheckDialog.showBlockedDialog(
                                IptvActivity.this,
                                () -> verifyAndAddXtream(name, server, username, password, mirrors),
                                () -> {
                                    long id = store.addXtream(name, server, username, password, mirrors);
                                    refresh();
                                    openSubscription(store.get(id));
                                }
                        );
                    }
                }
            }
        });
    }

    private void openSubscription(JSONObject sub) {
        if (sub == null) return;
        Intent intent = new Intent(this, IptvBrowseActivity.class);
        intent.putExtra("id", sub.optLong("id"));
        intent.putExtra("name", sub.optString("name"));
        intent.putExtra("type", sub.optString("type"));
        intent.putExtra("server", sub.optString("server"));
        intent.putExtra("username", sub.optString("username"));
        intent.putExtra("password", sub.optString("password"));
        intent.putExtra("url", sub.optString("url"));
        java.util.ArrayList<String> mirrors = new java.util.ArrayList<>(IptvStore.getMirrors(sub));
        if (mirrors.isEmpty()) {
            mirrors.addAll(java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS));
            store.setMirrors(sub.optLong("id"), mirrors);
        }
        intent.putStringArrayListExtra("mirrors", mirrors);
        startActivity(intent);
    }

    @Override
    public void onOpen(JSONObject subscription) {
        openSubscription(subscription);
    }

    @Override
    public void onDelete(JSONObject subscription) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.iptv_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    store.delete(subscription.optLong("id"));
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onLongClick(JSONObject subscription) {
        if (subscription == null) return;
        String type = subscription.optString("type", IptvStore.TYPE_XTREAM);
        if (!IptvStore.TYPE_XTREAM.equalsIgnoreCase(type)) {
            return;
        }

        String[] options = new String[]{
                getString(R.string.iptv_diagnose_mirrors),
                getString(R.string.delete)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(subscription.optString("name", getString(R.string.iptv_title)))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        IptvMirrorCheckDialog.show(this, apiClient, store, subscription, this::refresh);
                    } else if (which == 1) {
                        onDelete(subscription);
                    }
                })
                .show();
    }

    private boolean isVpnActive() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        for (android.net.Network n : cm.getAllNetworks()) {
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                return true;
            }
        }
        return false;
    }

    private void toggleTunnel() {
        PackageManager pm = getPackageManager();
        boolean installed;
        try {
            pm.getPackageInfo("com.wireguard.android", 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        if (!installed) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.tunnel_not_installed_title)
                    .setMessage(R.string.tunnel_not_installed_msg)
                    .setPositiveButton(R.string.tunnel_open_store, (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.wireguard.android")));
                        } catch (Exception ex) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.wireguard.android")));
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        if (isVpnActive()) {
            Toast.makeText(this, R.string.tunnel_active, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent("com.wireguard.android.action.TOGGLE_TUNNEL");
            i.putExtra("tunnel_name", "warp");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ex) {
            try {
                Intent open = getPackageManager().getLaunchIntentForPackage("com.wireguard.android");
                if (open != null) startActivity(open);
            } catch (Exception ignored) {
            }
            Toast.makeText(this, R.string.tunnel_open_wg_hint, Toast.LENGTH_LONG).show();
        }
    }
}
