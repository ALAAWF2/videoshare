package com.orange.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class IptvDetailsDialog {

    public interface OnSeriesClickListener {
        void onSeriesClick(IptvModels.Item seriesItem);
    }

    public static void show(
            Context context,
            IptvApiClient apiClient,
            IptvStore store,
            IptvModels.Item item,
            String server,
            List<String> mirrors,
            String username,
            String password,
            OnSeriesClickListener seriesListener
    ) {
        if (context == null || item == null) return;

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_iptv_details, null);

        ImageView ivBackdrop = v.findViewById(R.id.iv_details_backdrop);
        ImageView ivPoster = v.findViewById(R.id.iv_details_poster);
        ImageButton btnClose = v.findViewById(R.id.btn_close_details);

        TextView tvTitle = v.findViewById(R.id.tv_details_title);
        TextView tvRating = v.findViewById(R.id.tv_details_rating);
        TextView tvYear = v.findViewById(R.id.tv_details_year);
        TextView tvDuration = v.findViewById(R.id.tv_details_duration);
        TextView tvGenre = v.findViewById(R.id.tv_details_genre);
        TextView tvPlot = v.findViewById(R.id.tv_details_plot);
        TextView tvCastHeader = v.findViewById(R.id.tv_details_cast_header);
        TextView tvCast = v.findViewById(R.id.tv_details_cast);
        ProgressBar progressLoading = v.findViewById(R.id.progress_details_loading);

        MaterialButton btnPlay = v.findViewById(R.id.btn_details_play);
        MaterialButton btnDownload = v.findViewById(R.id.btn_details_download);
        ImageButton btnFavorite = v.findViewById(R.id.btn_details_favorite);
        ImageButton btnDirectLink = v.findViewById(R.id.btn_details_direct_link);
        ImageButton btnWatchParty = v.findViewById(R.id.btn_details_watch_party);
        MaterialButton btnViewEpisodes = v.findViewById(R.id.btn_details_view_episodes);

        tvTitle.setText(item.name);

        int placeholder = "series".equalsIgnoreCase(item.type) ? R.drawable.ic_series : R.drawable.ic_movie;
        IptvImageLoader.getInstance().load(ivBackdrop, item.iconUrl, placeholder);
        IptvImageLoader.getInstance().load(ivPoster, item.iconUrl, placeholder);

        if (item.rating != null && !item.rating.trim().isEmpty() && !"0".equals(item.rating)) {
            tvRating.setText("★ " + item.rating.trim());
            tvRating.setVisibility(View.VISIBLE);
        }

        if (item.year != null && !item.year.trim().isEmpty()) {
            tvYear.setText(item.year.trim());
            tvYear.setVisibility(View.VISIBLE);
        }

        if (item.genre != null && !item.genre.trim().isEmpty()) {
            tvGenre.setText(item.genre.trim());
            tvGenre.setVisibility(View.VISIBLE);
        }

        if (item.plot != null && !item.plot.trim().isEmpty()) {
            tvPlot.setText(item.plot.trim());
        }

        boolean isSeries = "series".equalsIgnoreCase(item.type);

        if (isSeries) {
            btnPlay.setVisibility(View.GONE);
            btnDownload.setVisibility(View.GONE);
            btnDirectLink.setVisibility(View.GONE);
            btnWatchParty.setVisibility(View.GONE);
            btnViewEpisodes.setVisibility(View.VISIBLE);
        } else {
            btnPlay.setVisibility(View.VISIBLE);
            btnDownload.setVisibility(View.VISIBLE);
            btnDirectLink.setVisibility(View.VISIBLE);
            btnWatchParty.setVisibility(View.VISIBLE);
            btnViewEpisodes.setVisibility(View.GONE);
        }

        // Setup Favorite button
        IptvStore iptvStore = (store != null) ? store : new IptvStore(context);
        boolean hasUrl = item.streamUrl != null && !item.streamUrl.trim().isEmpty();
        if (hasUrl) {
            boolean isFav = iptvStore.isFavorite(item.streamUrl);
            btnFavorite.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
            int accentColor = ContextCompat.getColor(context, R.color.accent);
            int dimColor = ContextCompat.getColor(context, R.color.dim);
            btnFavorite.setColorFilter(isFav ? accentColor : dimColor);

            btnFavorite.setOnClickListener(btn -> {
                boolean nowFav = iptvStore.isFavorite(item.streamUrl);
                if (nowFav) {
                    iptvStore.removeFavorite(item.streamUrl);
                    btnFavorite.setImageResource(R.drawable.ic_star_outline);
                    btnFavorite.setColorFilter(dimColor);
                    Toast.makeText(context, R.string.iptv_fav_removed, Toast.LENGTH_SHORT).show();
                } else {
                    iptvStore.addFavorite(item.name, item.streamUrl);
                    btnFavorite.setImageResource(R.drawable.ic_star_filled);
                    btnFavorite.setColorFilter(accentColor);
                    Toast.makeText(context, R.string.iptv_fav_added, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnFavorite.setVisibility(View.GONE);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(v)
                .setCancelable(true)
                .create();

        btnClose.setOnClickListener(btn -> dialog.dismiss());

        btnPlay.setOnClickListener(btn -> {
            if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("url", item.streamUrl);
                intent.putExtra("name", item.name);
                context.startActivity(intent);
                dialog.dismiss();
            } else {
                Toast.makeText(context, R.string.play_error, Toast.LENGTH_SHORT).show();
            }
        });

        btnDownload.setOnClickListener(btn -> {
            if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                DownloadHelper.startDownload(context, apiClient, item.name, item.streamUrl, item.iconUrl, null);
                dialog.dismiss();
            } else {
                Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show();
            }
        });

        btnDirectLink.setOnClickListener(btn -> {
            if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                IptvDirectLinkHelper.showDirectLinkDialog(context, apiClient, item.name, item.streamUrl);
            }
        });

        btnWatchParty.setOnClickListener(btn -> {
            if (item.streamUrl != null && !item.streamUrl.isEmpty()) {
                WatchPartyDialog.show(context, item.streamUrl, item.name, null);
            }
        });

        btnViewEpisodes.setOnClickListener(btn -> {
            dialog.dismiss();
            if (seriesListener != null) {
                seriesListener.onSeriesClick(item);
            }
        });

        // If it's a VOD (movie) and credentials exist, fetch extra info from server
        if ("vod".equalsIgnoreCase(item.type) && apiClient != null && server != null && !server.isEmpty()) {
            progressLoading.setVisibility(View.VISIBLE);
            apiClient.getVodInfo(server, mirrors, username, password, item.id, null, new IptvApiClient.Callback<IptvModels.VodDetails>() {
                @Override
                public void onSuccess(IptvModels.VodDetails details) {
                    if (!dialog.isShowing()) return;
                    progressLoading.setVisibility(View.GONE);

                    if (details.rating != null && !details.rating.isEmpty() && !"0".equals(details.rating)) {
                        tvRating.setText("★ " + details.rating);
                        tvRating.setVisibility(View.VISIBLE);
                    }
                    if (details.releaseDate != null && !details.releaseDate.isEmpty()) {
                        String yr = details.releaseDate;
                        if (yr.length() > 4) yr = yr.substring(0, 4);
                        tvYear.setText(yr);
                        tvYear.setVisibility(View.VISIBLE);
                    }
                    if (details.duration != null && !details.duration.isEmpty()) {
                        tvDuration.setText(details.duration);
                        tvDuration.setVisibility(View.VISIBLE);
                    }
                    if (details.genre != null && !details.genre.isEmpty()) {
                        tvGenre.setText(details.genre);
                        tvGenre.setVisibility(View.VISIBLE);
                    }
                    if (details.plot != null && !details.plot.isEmpty()) {
                        tvPlot.setText(details.plot);
                    }
                    if (details.cast != null && !details.cast.isEmpty()) {
                        tvCast.setText(details.cast);
                        tvCastHeader.setVisibility(View.VISIBLE);
                        tvCast.setVisibility(View.VISIBLE);
                    }
                    if (details.image != null && !details.image.isEmpty()) {
                        IptvImageLoader.getInstance().load(ivBackdrop, details.image, R.drawable.ic_movie);
                        IptvImageLoader.getInstance().load(ivPoster, details.image, R.drawable.ic_movie);
                    }
                }

                @Override
                public void onError(String error) {
                    if (!dialog.isShowing()) return;
                    progressLoading.setVisibility(View.GONE);
                }
            });
        }

        dialog.show();
    }
}
