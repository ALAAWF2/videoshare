package com.orange.videoplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends AppCompatActivity implements DownloadAdapter.Listener {

    private DownloadStore store;
    private MaterialToolbar toolbar;
    private TextView emptyView;
    private TextView resumingHintView;
    private RecyclerView recycler;
    private DownloadAdapter adapter;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            refreshList();
            pollHandler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        store = DownloadStore.getInstance(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyView = findViewById(R.id.tv_empty);
        resumingHintView = findViewById(R.id.tv_resuming_hint);
        recycler = findViewById(R.id.recycler_downloads);
        recycler.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
        pollHandler.post(pollTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollTask);
    }

    private void refreshList() {
        List<JSONObject> items = store.getAll();
        boolean hasPaused = false;
        for (JSONObject o : items) {
            int st = o.optInt("status");
            if (st == DownloadStore.STATUS_PAUSED) {
                hasPaused = true;
                break;
            }
        }

        if (resumingHintView != null) {
            resumingHintView.setVisibility(hasPaused ? View.VISIBLE : View.GONE);
        }

        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        toolbar.setSubtitle(getString(R.string.downloads_count, items.size()));

        if (adapter == null) {
            adapter = new DownloadAdapter(items, this);
            recycler.setAdapter(adapter);
        } else {
            adapter.updateItems(items);
        }
    }

    private long getDownloadId(JSONObject item) {
        if (item == null) return 0L;
        long id = item.optLong("downloadId", 0L);
        if (id <= 0) {
            id = item.optLong("id", 0L);
        }
        return id;
    }

    @Override
    public void onDownloadClick(JSONObject item) {
        if (item == null) return;
        int status = item.optInt("status");
        String filePath = item.optString("filePath");
        String localUri = item.optString("localUri");
        String title = item.optString("title", "فيديو محمل");
        long downloadId = getDownloadId(item);

        if (status == DownloadStore.STATUS_SUCCESSFUL) {
            File f = (filePath != null && !filePath.isEmpty()) ? new File(filePath) : null;
            if (f != null && f.exists() && f.length() > 0) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("url", Uri.fromFile(f).toString());
                intent.putExtra("name", title);
                startActivity(intent);
            } else if (localUri != null && !localUri.isEmpty()) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("url", localUri);
                intent.putExtra("name", title);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.download_file_not_found, Toast.LENGTH_SHORT).show();
            }
        } else if (status == DownloadStore.STATUS_RUNNING || status == DownloadStore.STATUS_PENDING) {
            showRunningOptionsDialog(item, downloadId, title);
        } else if (status == DownloadStore.STATUS_PAUSED || status == DownloadStore.STATUS_FAILED) {
            showPausedOptionsDialog(item, downloadId, title);
        }
    }

    @Override
    public void onDownloadLongClick(JSONObject item) {
        if (item == null) return;
        int status = item.optInt("status");
        String title = item.optString("title", "فيديو محمل");
        long downloadId = getDownloadId(item);

        if (status == DownloadStore.STATUS_SUCCESSFUL) {
            CharSequence[] options = new CharSequence[]{
                    getString(R.string.iptv_play_btn),
                    getString(R.string.iptv_share),
                    getString(R.string.delete)
            };
            new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            onDownloadClick(item);
                        } else if (which == 1) {
                            onDownloadShare(item);
                        } else if (which == 2) {
                            onDownloadDelete(item);
                        }
                    })
                    .show();
        } else if (status == DownloadStore.STATUS_RUNNING || status == DownloadStore.STATUS_PENDING) {
            showRunningOptionsDialog(item, downloadId, title);
        } else {
            showPausedOptionsDialog(item, downloadId, title);
        }
    }

    private void showRunningOptionsDialog(JSONObject item, long downloadId, String title) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.download_pause),
                getString(R.string.download_cancel)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        DownloadHelper.pauseDownload(this, downloadId);
                        refreshList();
                    } else if (which == 1) {
                        onDownloadDelete(item);
                    }
                })
                .show();
    }

    private void showPausedOptionsDialog(JSONObject item, long downloadId, String title) {
        CharSequence[] options = new CharSequence[]{
                getString(R.string.download_resume),
                getString(R.string.delete)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        DownloadHelper.resumeDownload(this, downloadId);
                        refreshList();
                    } else if (which == 1) {
                        onDownloadDelete(item);
                    }
                })
                .show();
    }

    @Override
    public void onDownloadDelete(JSONObject item) {
        if (item == null) return;
        long downloadId = getDownloadId(item);
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.download_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    DownloadHelper.cancelDownload(this, downloadId);
                    store.delete(downloadId);
                    refreshList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDownloadShare(JSONObject item) {
        if (item == null) return;
        String filePath = item.optString("filePath");
        if (filePath != null && !filePath.isEmpty()) {
            File f = new File(filePath);
            if (f.exists()) {
                try {
                    Uri contentUri = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            ? FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f)
                            : Uri.fromFile(f);

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("video/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.iptv_share)));
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        String url = item.optString("url");
        if (!url.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(intent, getString(R.string.iptv_share)));
        }
    }
}
