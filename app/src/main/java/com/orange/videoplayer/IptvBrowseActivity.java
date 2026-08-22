package com.orange.videoplayer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IptvBrowseActivity extends AppCompatActivity {

    private IptvApiClient apiClient;
    private IptvStore store;
    private long subId = -1;
    private List<String> mirrors;

    private String subType;
    private String server;
    private String username;
    private String password;
    private String m3uUrl;
    private String subName;

    private String currentSection = "live"; // "live", "vod", "series"
    private String currentCategoryId = "all";
    private boolean isGridMode = false;

    private MaterialToolbar toolbar;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private LinearLayout containerSectionTabs;
    private TextView tabLive;
    private TextView tabVod;
    private TextView tabSeries;
    private RecyclerView recyclerCategories;
    private RecyclerView recyclerItems;
    private View layoutLoading;
    private TextView tvLoadingText;
    private TextView tvEmpty;

    private IptvCategoryAdapter categoryAdapter;
    private IptvItemAdapter itemAdapter;

    private final List<IptvModels.Category> categoriesList = new ArrayList<>();
    private final List<IptvModels.Item> currentItemsList = new ArrayList<>();

    // For M3U storage
    private Map<IptvModels.Category, List<IptvModels.Item>> m3uData;

    private final IptvApiClient.OnServerChangeListener serverChangeListener = newWorkingServer -> {
        server = newWorkingServer;
        toolbar.setSubtitle(IptvStore.TYPE_M3U.equalsIgnoreCase(subType) ? getString(R.string.iptv_type_m3u) : server);
        if (subId > 0 && store != null) {
            store.updateServer(subId, newWorkingServer);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsStore settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iptv_browse);

        store = new IptvStore(this);
        apiClient = new IptvApiClient();

        subId = getIntent().getLongExtra("id", -1);
        subType = getIntent().getStringExtra("type");
        if (subType == null) subType = IptvStore.TYPE_XTREAM;
        server = getIntent().getStringExtra("server");
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        m3uUrl = getIntent().getStringExtra("url");
        subName = getIntent().getStringExtra("name");

        mirrors = getIntent().getStringArrayListExtra("mirrors");
        if ((mirrors == null || mirrors.isEmpty()) && subId > 0) {
            mirrors = store.getMirrors(subId);
        }
        if ((mirrors == null || mirrors.isEmpty()) && server != null && (server.contains("alico20") || server.contains("tg7080"))) {
            mirrors = java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS);
        }

        initViews();
        setupSearch();
        setupTabs();

        if (IptvStore.TYPE_M3U.equalsIgnoreCase(subType)) {
            containerSectionTabs.setVisibility(View.GONE);
            loadM3uContent();
        } else {
            containerSectionTabs.setVisibility(View.VISIBLE);
            loadXtreamSection(currentSection);
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(subName != null ? subName : getString(R.string.iptv_title));
        toolbar.setSubtitle(IptvStore.TYPE_M3U.equalsIgnoreCase(subType) ? getString(R.string.iptv_type_m3u) : server);
        toolbar.setNavigationOnClickListener(v -> finish());

        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.getMenu().clear();
        toolbar.getMenu().add(0, 201, 0, R.string.toggle_grid)
                .setIcon(R.drawable.ic_grid)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 201) {
                toggleLayoutMode();
                return true;
            }
            return false;
        });

        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        containerSectionTabs = findViewById(R.id.container_section_tabs);
        tabLive = findViewById(R.id.tab_live);
        tabVod = findViewById(R.id.tab_vod);
        tabSeries = findViewById(R.id.tab_series);
        recyclerCategories = findViewById(R.id.recycler_categories);
        recyclerItems = findViewById(R.id.recycler_items);
        layoutLoading = findViewById(R.id.layout_loading);
        tvLoadingText = findViewById(R.id.tv_loading_text);
        tvEmpty = findViewById(R.id.tv_empty);

        recyclerCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new IptvCategoryAdapter(categoriesList, this::onCategorySelected);
        recyclerCategories.setAdapter(categoryAdapter);

        itemAdapter = new IptvItemAdapter(currentItemsList, this::onItemClicked, this::onItemLongClicked);
        updateLayoutManager();
        recyclerItems.setAdapter(itemAdapter);
    }

    private void toggleLayoutMode() {
        isGridMode = !isGridMode;
        MenuItem item = toolbar.getMenu().findItem(201);
        if (item != null) {
            item.setIcon(isGridMode ? R.drawable.ic_list : R.drawable.ic_grid);
            item.setTitle(isGridMode ? R.string.toggle_list : R.string.toggle_grid);
        }
        updateLayoutManager();
    }

    private void updateLayoutManager() {
        if (itemAdapter != null) {
            itemAdapter.setGridMode(isGridMode);
        }
        if (isGridMode) {
            recyclerItems.setLayoutManager(new GridLayoutManager(this, 3));
        } else {
            recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (itemAdapter != null) {
                    itemAdapter.filter(query);
                    checkEmptyState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    private void setupTabs() {
        tabLive.setOnClickListener(v -> selectSection("live"));
        tabVod.setOnClickListener(v -> selectSection("vod"));
        tabSeries.setOnClickListener(v -> selectSection("series"));
    }

    private void selectSection(String section) {
        if (currentSection.equals(section)) return;
        currentSection = section;
        etSearch.setText("");

        int accentColor = ContextCompat.getColor(this, R.color.accent);
        int dimColor = ContextCompat.getColor(this, R.color.dim);

        tabLive.setBackgroundResource("live".equals(section) ? R.drawable.bg_hold_indicator : 0);
        tabLive.setTextColor("live".equals(section) ? accentColor : dimColor);

        tabVod.setBackgroundResource("vod".equals(section) ? R.drawable.bg_hold_indicator : 0);
        tabVod.setTextColor("vod".equals(section) ? accentColor : dimColor);

        tabSeries.setBackgroundResource("series".equals(section) ? R.drawable.bg_hold_indicator : 0);
        tabSeries.setTextColor("series".equals(section) ? accentColor : dimColor);

        // Auto switch layout for Movies/Series (Grid) vs Live (List)
        boolean shouldBeGrid = "vod".equals(section) || "series".equals(section);
        if (isGridMode != shouldBeGrid) {
            isGridMode = shouldBeGrid;
            MenuItem item = toolbar.getMenu().findItem(201);
            if (item != null) {
                item.setIcon(isGridMode ? R.drawable.ic_list : R.drawable.ic_grid);
            }
            updateLayoutManager();
        }

        loadXtreamSection(section);
    }

    private void loadXtreamSection(String section) {
        showLoading(true, getString(R.string.iptv_loading));
        currentCategoryId = "all";
        categoriesList.clear();
        currentItemsList.clear();
        categoryAdapter.notifyDataSetChanged();
        itemAdapter.updateData(currentItemsList);

        apiClient.getCategories(server, mirrors, username, password, section, serverChangeListener, new IptvApiClient.Callback<List<IptvModels.Category>>() {
            @Override
            public void onSuccess(List<IptvModels.Category> categories) {
                categoriesList.clear();
                categoriesList.addAll(categories);
                categoryAdapter.setSelectedIndex(0);
                categoryAdapter.notifyDataSetChanged();

                loadXtreamItems(section, "all");
            }

            @Override
            public void onError(String error) {
                showLoading(false, "");
                Toast.makeText(IptvBrowseActivity.this, error, Toast.LENGTH_SHORT).show();
                checkEmptyState();
            }
        });
    }

    private void loadXtreamItems(String section, String categoryId) {
        showLoading(true, getString(R.string.iptv_loading));
        apiClient.getItems(server, mirrors, username, password, section, categoryId, serverChangeListener, new IptvApiClient.Callback<List<IptvModels.Item>>() {
            @Override
            public void onSuccess(List<IptvModels.Item> items) {
                showLoading(false, "");
                currentItemsList.clear();
                currentItemsList.addAll(items);
                itemAdapter.updateData(currentItemsList);
                checkEmptyState();
            }

            @Override
            public void onError(String error) {
                showLoading(false, "");
                Toast.makeText(IptvBrowseActivity.this, error, Toast.LENGTH_SHORT).show();
                checkEmptyState();
            }
        });
    }

    private void loadM3uContent() {
        showLoading(true, getString(R.string.iptv_loading));
        apiClient.parseM3uPlaylist(m3uUrl, new IptvApiClient.Callback<Map<IptvModels.Category, List<IptvModels.Item>>>() {
            @Override
            public void onSuccess(Map<IptvModels.Category, List<IptvModels.Item>> data) {
                showLoading(false, "");
                m3uData = data;
                categoriesList.clear();
                categoriesList.addAll(data.keySet());
                categoryAdapter.setSelectedIndex(0);
                categoryAdapter.notifyDataSetChanged();

                if (!categoriesList.isEmpty()) {
                    IptvModels.Category firstCat = categoriesList.get(0);
                    List<IptvModels.Item> items = m3uData.get(firstCat);
                    currentItemsList.clear();
                    if (items != null) currentItemsList.addAll(items);
                    itemAdapter.updateData(currentItemsList);
                }
                checkEmptyState();
            }

            @Override
            public void onError(String error) {
                showLoading(false, "");
                Toast.makeText(IptvBrowseActivity.this, error, Toast.LENGTH_SHORT).show();
                checkEmptyState();
            }
        });
    }

    private void onCategorySelected(IptvModels.Category category) {
        currentCategoryId = category.id;
        etSearch.setText("");

        if (IptvStore.TYPE_M3U.equalsIgnoreCase(subType)) {
            if (m3uData != null) {
                List<IptvModels.Item> items = m3uData.get(category);
                currentItemsList.clear();
                if (items != null) currentItemsList.addAll(items);
                itemAdapter.updateData(currentItemsList);
                checkEmptyState();
            }
        } else {
            loadXtreamItems(currentSection, category.id);
        }
    }

    private void onItemClicked(IptvModels.Item item) {
        if ("vod".equalsIgnoreCase(item.type) || "series".equalsIgnoreCase(item.type)) {
            // Show rich details dialog
            IptvDetailsDialog.show(
                    this,
                    apiClient,
                    store,
                    item,
                    server,
                    mirrors,
                    username,
                    password,
                    this::openSeriesEpisodes
            );
        } else {
            // Live stream
            if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("url", item.streamUrl);
                intent.putExtra("name", item.name);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.play_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSeriesEpisodes(IptvModels.Item seriesItem) {
        Intent intent = new Intent(this, IptvSeriesActivity.class);
        intent.putExtra("id", subId);
        intent.putExtra("server", server);
        if (mirrors != null) {
            intent.putStringArrayListExtra("mirrors", new java.util.ArrayList<>(mirrors));
        }
        intent.putExtra("username", username);
        intent.putExtra("password", password);
        intent.putExtra("series_id", seriesItem.id);
        intent.putExtra("name", seriesItem.name);
        startActivity(intent);
    }

    private boolean onItemLongClicked(IptvModels.Item item) {
        if (item != null && item.streamUrl != null && !item.streamUrl.trim().isEmpty()) {
            IptvDirectLinkHelper.showDirectLinkDialog(this, apiClient, item.name, item.streamUrl);
            return true;
        }
        return false;
    }

    private void showLoading(boolean show, String text) {
        layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            tvLoadingText.setText(text);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void checkEmptyState() {
        if (layoutLoading.getVisibility() == View.VISIBLE) {
            tvEmpty.setVisibility(View.GONE);
            return;
        }
        tvEmpty.setVisibility(itemAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
