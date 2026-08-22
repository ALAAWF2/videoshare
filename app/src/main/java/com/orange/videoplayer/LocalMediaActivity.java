package com.orange.videoplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class LocalMediaActivity extends AppCompatActivity implements LocalMediaAdapter.Listener {

    private MaterialToolbar toolbar;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tabVideos;
    private TextView tabAudio;
    private TextView tabAll;

    private RecyclerView recycler;
    private View layoutLoading;
    private TextView tvEmpty;
    private View layoutPermission;
    private MaterialButton btnGrantPermission;

    private LocalMediaAdapter adapter;
    private LocalMediaScanner.ScanResult currentScanResult;
    private String currentTab = "videos"; // "videos", "audio", "all"

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!Boolean.TRUE.equals(granted)) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted || hasPermissions()) {
                    layoutPermission.setVisibility(View.GONE);
                    loadMedia();
                } else {
                    layoutPermission.setVisibility(View.VISIBLE);
                    layoutLoading.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsStore settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_media);

        initViews();
        setupSearch();
        setupTabs();

        if (hasPermissions()) {
            loadMedia();
        } else {
            requestPermissions();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        tabVideos = findViewById(R.id.tab_videos);
        tabAudio = findViewById(R.id.tab_audio);
        tabAll = findViewById(R.id.tab_all);

        recycler = findViewById(R.id.recycler_local_media);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LocalMediaAdapter(new ArrayList<>(), this);
        recycler.setAdapter(adapter);

        layoutLoading = findViewById(R.id.layout_loading);
        tvEmpty = findViewById(R.id.tv_empty);
        layoutPermission = findViewById(R.id.layout_permission);
        btnGrantPermission = findViewById(R.id.btn_grant_permission);

        btnGrantPermission.setOnClickListener(v -> requestPermissions());
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString();
                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (adapter != null) {
                    adapter.filter(q);
                    checkEmptyState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    private void setupTabs() {
        tabVideos.setOnClickListener(v -> selectTab("videos"));
        tabAudio.setOnClickListener(v -> selectTab("audio"));
        tabAll.setOnClickListener(v -> selectTab("all"));
    }

    private void selectTab(String tab) {
        if (currentTab.equals(tab)) return;
        currentTab = tab;
        etSearch.setText("");

        int accentColor = ContextCompat.getColor(this, R.color.accent);
        int dimColor = ContextCompat.getColor(this, R.color.dim);

        tabVideos.setBackgroundResource("videos".equals(tab) ? R.drawable.bg_hold_indicator : 0);
        tabVideos.setTextColor("videos".equals(tab) ? accentColor : dimColor);

        tabAudio.setBackgroundResource("audio".equals(tab) ? R.drawable.bg_hold_indicator : 0);
        tabAudio.setTextColor("audio".equals(tab) ? accentColor : dimColor);

        tabAll.setBackgroundResource("all".equals(tab) ? R.drawable.bg_hold_indicator : 0);
        tabAll.setTextColor("all".equals(tab) ? accentColor : dimColor);

        displayCurrentTabItems();
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            });
        }
    }

    private void loadMedia() {
        layoutPermission.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        LocalMediaScanner.scanMediaAsync(this, result -> {
            layoutLoading.setVisibility(View.GONE);
            currentScanResult = result;
            displayCurrentTabItems();
        });
    }

    private void displayCurrentTabItems() {
        if (currentScanResult == null) return;
        List<LocalMediaItem> items;
        if ("audio".equals(currentTab)) {
            items = currentScanResult.audios;
        } else if ("all".equals(currentTab)) {
            items = currentScanResult.all;
        } else {
            items = currentScanResult.videos;
        }

        adapter.updateData(items);
        toolbar.setSubtitle(getString(R.string.local_media_count, items.size()));
        checkEmptyState();
    }

    private void checkEmptyState() {
        if (layoutLoading.getVisibility() == View.VISIBLE || layoutPermission.getVisibility() == View.VISIBLE) {
            tvEmpty.setVisibility(View.GONE);
            return;
        }
        boolean isEmpty = (adapter.getItemCount() == 0);
        if (isEmpty) {
            if ("audio".equals(currentTab)) {
                tvEmpty.setText(R.string.local_media_empty_audio);
            } else if ("videos".equals(currentTab)) {
                tvEmpty.setText(R.string.local_media_empty_videos);
            } else {
                tvEmpty.setText(R.string.local_media_empty);
            }
        }
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onMediaClick(LocalMediaItem item) {
        if (item == null || item.contentUri == null) return;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("url", item.contentUri.toString());
        intent.putExtra("name", item.title);
        startActivity(intent);
    }

    @Override
    public void onMediaShare(LocalMediaItem item) {
        if (item == null || item.contentUri == null) return;
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(item.isVideo ? "video/*" : "audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, item.contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.iptv_share)));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر مشاركة الملف", Toast.LENGTH_SHORT).show();
        }
    }
}
