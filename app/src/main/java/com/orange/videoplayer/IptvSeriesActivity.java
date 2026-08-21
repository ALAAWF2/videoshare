package com.orange.videoplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class IptvSeriesActivity extends AppCompatActivity {

    private IptvApiClient apiClient;
    private IptvStore store;
    private long subId = -1;
    private List<String> mirrors;

    private MaterialToolbar toolbar;
    private RecyclerView recyclerSeasons;
    private RecyclerView recyclerEpisodes;
    private ProgressBar progressLoading;
    private TextView tvEmpty;

    private String server;
    private String username;
    private String password;
    private String seriesId;
    private String seriesName;

    private IptvModels.Season currentSeason;

    private final IptvApiClient.OnServerChangeListener serverChangeListener = newWorkingServer -> {
        server = newWorkingServer;
        if (subId > 0 && store != null) {
            store.updateServer(subId, newWorkingServer);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_iptv_series);

        store = new IptvStore(this);
        apiClient = new IptvApiClient();

        subId = getIntent().getLongExtra("id", -1);
        server = getIntent().getStringExtra("server");
        username = getIntent().getStringExtra("username");
        password = getIntent().getStringExtra("password");
        seriesId = getIntent().getStringExtra("series_id");
        seriesName = getIntent().getStringExtra("name");

        mirrors = getIntent().getStringArrayListExtra("mirrors");
        if ((mirrors == null || mirrors.isEmpty()) && subId > 0) {
            mirrors = store.getMirrors(subId);
        }
        if ((mirrors == null || mirrors.isEmpty()) && server != null && (server.contains("alico20") || server.contains("tg7080"))) {
            mirrors = java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS);
        }

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(seriesName != null ? seriesName : getString(R.string.iptv_section_series));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_iptv_series);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_batch_links) {
                exportCurrentSeasonLinks();
                return true;
            }
            return false;
        });

        recyclerSeasons = findViewById(R.id.recycler_seasons);
        recyclerEpisodes = findViewById(R.id.recycler_episodes);
        progressLoading = findViewById(R.id.progress_loading);
        tvEmpty = findViewById(R.id.tv_empty);

        recyclerSeasons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerEpisodes.setLayoutManager(new LinearLayoutManager(this));

        loadSeriesInfo();
    }

    private void loadSeriesInfo() {
        progressLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        recyclerSeasons.setVisibility(View.GONE);
        recyclerEpisodes.setVisibility(View.GONE);

        apiClient.getSeriesInfo(server, mirrors, username, password, seriesId, serverChangeListener, new IptvApiClient.Callback<List<IptvModels.Season>>() {
            @Override
            public void onSuccess(List<IptvModels.Season> seasons) {
                progressLoading.setVisibility(View.GONE);
                if (seasons.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }

                recyclerSeasons.setVisibility(View.VISIBLE);
                recyclerEpisodes.setVisibility(View.VISIBLE);

                IptvSeasonAdapter seasonAdapter = new IptvSeasonAdapter(seasons, season -> {
                    showEpisodesForSeason(season);
                });
                recyclerSeasons.setAdapter(seasonAdapter);

                // Show first season episodes by default
                showEpisodesForSeason(seasons.get(0));
            }

            @Override
            public void onError(String error) {
                progressLoading.setVisibility(View.GONE);
                tvEmpty.setText(error);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(IptvSeriesActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEpisodesForSeason(IptvModels.Season season) {
        this.currentSeason = season;
        IptvEpisodeAdapter episodeAdapter = new IptvEpisodeAdapter(season.episodes, new IptvEpisodeAdapter.Listener() {
            @Override
            public void onEpisodeClick(IptvModels.Episode episode) {
                Intent intent = new Intent(IptvSeriesActivity.this, PlayerActivity.class);
                intent.putExtra("url", episode.streamUrl);
                String title = (seriesName != null ? seriesName + " - " : "") + episode.title;
                intent.putExtra("name", title);
                startActivity(intent);
            }

            @Override
            public void onEpisodeLinkClick(IptvModels.Episode episode) {
                String title = (seriesName != null ? seriesName + " - " : "") + episode.title;
                IptvDirectLinkHelper.showDirectLinkDialog(IptvSeriesActivity.this, apiClient, title, episode.streamUrl);
            }

            @Override
            public void onEpisodeDownloadClick(IptvModels.Episode episode) {
                String title = (seriesName != null ? seriesName + " - " : "") + episode.title;
                DownloadHelper.startDownload(IptvSeriesActivity.this, apiClient, title, episode.streamUrl, episode.iconUrl, null);
            }
        });
        recyclerEpisodes.setAdapter(episodeAdapter);
    }

    private void exportCurrentSeasonLinks() {
        if (currentSeason == null || currentSeason.episodes == null || currentSeason.episodes.isEmpty()) {
            Toast.makeText(this, R.string.iptv_no_episodes_for_season, Toast.LENGTH_SHORT).show();
            return;
        }
        IptvDirectLinkHelper.showBatchSeasonLinksDialog(this, seriesName, currentSeason);
    }
}
