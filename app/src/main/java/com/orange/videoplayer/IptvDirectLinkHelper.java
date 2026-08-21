package com.orange.videoplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IptvDirectLinkHelper {

    public static void showDirectLinkDialog(Context context, IptvApiClient apiClient, String title, String streamUrl) {
        if (context == null || streamUrl == null || streamUrl.trim().isEmpty()) {
            if (context != null) {
                Toast.makeText(context, R.string.bad_url, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_direct_link, null);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        View layoutLoading = dialogView.findViewById(R.id.layout_loading);
        View layoutResult = dialogView.findViewById(R.id.layout_result);
        View layoutError = dialogView.findViewById(R.id.layout_error);
        TextView tvResolvedUrl = dialogView.findViewById(R.id.tv_resolved_url);
        TextView tvErrorMessage = dialogView.findViewById(R.id.tv_error_message);
        Button btnCopy = dialogView.findViewById(R.id.btn_copy);
        Button btnShare = dialogView.findViewById(R.id.btn_share);
        Button btnPlay = dialogView.findViewById(R.id.btn_play);
        Button btnRetry = dialogView.findViewById(R.id.btn_retry);
        Button btnClose = dialogView.findViewById(R.id.btn_close);

        String displayTitle = title != null && !title.trim().isEmpty() ? title : context.getString(R.string.iptv_direct_link);
        tvDialogTitle.setText(displayTitle);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        Runnable[] resolveTask = new Runnable[1];
        resolveTask[0] = () -> {
            layoutLoading.setVisibility(View.VISIBLE);
            layoutResult.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);

            apiClient.resolveDirectUrl(streamUrl, new IptvApiClient.Callback<String>() {
                @Override
                public void onSuccess(String resolvedUrl) {
                    if (!dialog.isShowing()) return;
                    layoutLoading.setVisibility(View.GONE);
                    layoutResult.setVisibility(View.VISIBLE);
                    layoutError.setVisibility(View.GONE);

                    tvResolvedUrl.setText(resolvedUrl);

                    btnCopy.setOnClickListener(v -> copyToClipboard(context, resolvedUrl, context.getString(R.string.iptv_link_copied)));

                    btnShare.setOnClickListener(v -> shareText(context, resolvedUrl, displayTitle));

                    btnPlay.setOnClickListener(v -> {
                        Intent intent = new Intent(context, PlayerActivity.class);
                        intent.putExtra("url", resolvedUrl);
                        intent.putExtra("name", displayTitle);
                        context.startActivity(intent);
                        dialog.dismiss();
                    });
                }

                @Override
                public void onError(String error) {
                    if (!dialog.isShowing()) return;
                    layoutLoading.setVisibility(View.GONE);
                    layoutResult.setVisibility(View.GONE);
                    layoutError.setVisibility(View.VISIBLE);
                    tvErrorMessage.setText(error != null && !error.isEmpty() ? error : context.getString(R.string.iptv_failed_link));

                    btnRetry.setOnClickListener(v -> resolveTask[0].run());
                }
            });
        };

        dialog.show();
        resolveTask[0].run();
    }

    public static void showBatchSeasonLinksDialog(Context context, String seriesName, IptvModels.Season season) {
        if (context == null || season == null || season.episodes == null || season.episodes.isEmpty()) {
            if (context != null) {
                Toast.makeText(context, R.string.iptv_no_episodes_for_season, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        List<IptvModels.Episode> episodes = season.episodes;
        int total = episodes.size();

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_iptv_batch_links, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvDialogSubtitle = dialogView.findViewById(R.id.tv_dialog_subtitle);
        View layoutLoading = dialogView.findViewById(R.id.layout_loading);
        LinearProgressIndicator progressBatch = dialogView.findViewById(R.id.progress_batch);
        TextView tvProgressText = dialogView.findViewById(R.id.tv_progress_text);
        Button btnCancelBatch = dialogView.findViewById(R.id.btn_cancel_batch);

        View layoutResult = dialogView.findViewById(R.id.layout_result);
        TextView tvBatchContent = dialogView.findViewById(R.id.tv_batch_content);
        Button btnCopyAll = dialogView.findViewById(R.id.btn_copy_all);
        Button btnShareAll = dialogView.findViewById(R.id.btn_share_all);
        Button btnClose = dialogView.findViewById(R.id.btn_close);

        String sName = seriesName != null ? seriesName.trim() : "";
        String seasonName = season.name != null && !season.name.trim().isEmpty() ? season.name.trim() : context.getString(R.string.iptv_season_number, season.seasonNumber);
        tvDialogTitle.setText(R.string.iptv_all_season_links);
        tvDialogSubtitle.setText(!sName.isEmpty() ? (sName + " - " + seasonName) : seasonName);

        progressBatch.setMax(total);
        progressBatch.setProgress(0);
        tvProgressText.setText(context.getString(R.string.iptv_batch_progress, 0, total));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        AtomicBoolean isCancelled = new AtomicBoolean(false);
        ExecutorService batchPool = Executors.newFixedThreadPool(6);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        dialog.setOnDismissListener(d -> {
            isCancelled.set(true);
            batchPool.shutdownNow();
        });

        btnCancelBatch.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        String[] results = new String[total];
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final IptvModels.Episode ep = episodes.get(index);
            batchPool.execute(() -> {
                if (isCancelled.get()) return;
                String resolved;
                try {
                    resolved = IptvApiClient.resolveDirectUrlSync(ep.streamUrl);
                } catch (Exception e) {
                    resolved = null;
                }
                if (isCancelled.get()) return;
                results[index] = resolved;
                int currentDone = completedCount.incrementAndGet();

                mainHandler.post(() -> {
                    if (isCancelled.get() || !dialog.isShowing()) return;
                    progressBatch.setProgress(currentDone);
                    tvProgressText.setText(context.getString(R.string.iptv_batch_progress, currentDone, total));

                    if (currentDone == total) {
                        String formattedText = buildBatchExportText(context, seriesName, season, episodes, results);
                        layoutLoading.setVisibility(View.GONE);
                        layoutResult.setVisibility(View.VISIBLE);
                        tvBatchContent.setText(formattedText);

                        btnCopyAll.setOnClickListener(v -> copyToClipboard(context, formattedText, context.getString(R.string.iptv_all_links_copied)));

                        btnShareAll.setOnClickListener(v -> shareText(context, formattedText, tvDialogSubtitle.getText().toString()));
                    }
                });
            });
        }

        dialog.show();
    }

    private static String buildBatchExportText(Context context, String seriesName, IptvModels.Season season, List<IptvModels.Episode> episodes, String[] results) {
        StringBuilder sb = new StringBuilder();
        String sName = seriesName != null ? seriesName.trim() : "";
        String seasonName = season.name != null && !season.name.trim().isEmpty() ? season.name.trim() : context.getString(R.string.iptv_season_number, season.seasonNumber);

        if (!sName.isEmpty()) {
            sb.append(context.getString(R.string.iptv_batch_header, sName, seasonName)).append("\n\n");
        } else {
            sb.append(context.getString(R.string.iptv_all_season_links)).append(" - ").append(seasonName).append(":\n\n");
        }

        int seasonInt = 1;
        try {
            seasonInt = Integer.parseInt(season.seasonNumber.replaceAll("\\D+", ""));
        } catch (Exception ignored) {}

        for (int i = 0; i < episodes.size(); i++) {
            IptvModels.Episode ep = episodes.get(i);
            int epInt = i + 1;
            try {
                epInt = Integer.parseInt(ep.episodeNum.replaceAll("\\D+", ""));
            } catch (Exception ignored) {}

            String tag = String.format(Locale.US, "S%02dE%02d", seasonInt, epInt);
            String title = ep.title != null && !ep.title.trim().isEmpty() ? ep.title.trim() : (context.getString(R.string.iptv_episodes) + " " + ep.episodeNum);

            sb.append(tag).append(" - ").append(title).append("\n");
            String url = results != null && i < results.length ? results[i] : null;
            if (url != null && !url.trim().isEmpty()) {
                sb.append(url.trim()).append("\n");
            } else {
                sb.append(context.getString(R.string.iptv_failed_link)).append("\n");
            }
            if (i < episodes.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static void copyToClipboard(Context context, String text, String toastMessage) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Direct Stream URL", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareText(Context context, String text, String subject) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        if (subject != null && !subject.isEmpty()) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.iptv_share)));
    }
}