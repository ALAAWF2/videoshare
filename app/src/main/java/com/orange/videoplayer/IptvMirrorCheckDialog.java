package com.orange.videoplayer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class IptvMirrorCheckDialog {

    public static void openWarpPlayStore(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.cloudflare.onedotonedotonedotone"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(context, R.string.open_warp_error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void showBlockedDialog(Context context, Runnable onRetry, Runnable onSaveAnyway) {
        if (context == null) return;
        View v = LayoutInflater.from(context).inflate(R.layout.dialog_iptv_blocked, null);
        MaterialButton btnWarp = v.findViewById(R.id.btn_warp_install);
        btnWarp.setOnClickListener(x -> openWarpPlayStore(context));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(v)
                .setPositiveButton(R.string.iptv_retry_probe, (d, w) -> {
                    if (onRetry != null) onRetry.run();
                })
                .setNegativeButton(R.string.cancel, null);

        if (onSaveAnyway != null) {
            builder.setNeutralButton(R.string.save_anyway, (d, w) -> onSaveAnyway.run());
        }

        builder.show();
    }

    public static void show(Context context, IptvApiClient apiClient, IptvStore store, JSONObject subscription, Runnable onUpdated) {
        if (context == null || subscription == null || apiClient == null) return;

        long subId = subscription.optLong("id");
        String server = subscription.optString("server");
        String username = subscription.optString("username");
        String password = subscription.optString("password");
        List<String> mirrors = IptvStore.getMirrors(subscription);
        if (mirrors.isEmpty()) {
            mirrors = java.util.Arrays.asList(IptvStore.DEFAULT_ACTION_TV_MIRRORS);
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_mirror_check, null);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvProgressText = dialogView.findViewById(R.id.tv_progress_text);
        LinearProgressIndicator progressIndicator = dialogView.findViewById(R.id.progress_indicator);
        RecyclerView recyclerMirrors = dialogView.findViewById(R.id.recycler_mirrors);
        View layoutAllFail = dialogView.findViewById(R.id.layout_all_fail);
        MaterialButton btnWarpDiag = dialogView.findViewById(R.id.btn_warp_diag);
        MaterialButton btnRetryDiag = dialogView.findViewById(R.id.btn_retry_diag);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_close);
        MaterialButton btnUseWorking = dialogView.findViewById(R.id.btn_use_working);

        String subName = subscription.optString("name", context.getString(R.string.iptv_title));
        tvDialogTitle.setText(subName + " - " + context.getString(R.string.iptv_mirror_check_title));

        List<IptvApiClient.MirrorHostResult> items = new ArrayList<>();
        ProbeAdapter adapter = new ProbeAdapter(context, items);
        recyclerMirrors.setLayoutManager(new LinearLayoutManager(context));
        recyclerMirrors.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnWarpDiag.setOnClickListener(v -> openWarpPlayStore(context));

        adapter.setOnSelectionChangedListener(selectedResult -> {
            btnUseWorking.setEnabled(selectedResult != null && selectedResult.status == IptvApiClient.MirrorStatus.OK);
        });

        final List<String> finalMirrors = mirrors;
        Runnable runProbe = new Runnable() {
            @Override
            public void run() {
                items.clear();
                adapter.setSelectedIndex(-1);
                adapter.notifyDataSetChanged();

                btnUseWorking.setEnabled(false);
                layoutAllFail.setVisibility(View.GONE);
                tvProgressText.setText(R.string.iptv_mirror_probing);
                progressIndicator.setIndeterminate(true);

                apiClient.probeMirrors(server, finalMirrors, username, password, new IptvApiClient.MirrorProbeCallback() {
                    @Override
                    public void onHostProbed(IptvApiClient.MirrorHostResult hostResult, int currentIndex, int totalCount) {
                        if (!dialog.isShowing()) return;
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(totalCount);
                        progressIndicator.setProgress(currentIndex);
                        tvProgressText.setText(context.getString(R.string.iptv_mirror_probing_progress, currentIndex, totalCount));

                        items.add(hostResult);
                        adapter.notifyItemInserted(items.size() - 1);
                        recyclerMirrors.scrollToPosition(items.size() - 1);

                        if (hostResult.status == IptvApiClient.MirrorStatus.OK && adapter.getSelectedIndex() < 0) {
                            adapter.setSelectedIndex(items.size() - 1);
                            btnUseWorking.setEnabled(true);
                        }
                    }

                    @Override
                    public void onComplete(IptvApiClient.MirrorProbeSummary summary) {
                        if (!dialog.isShowing()) return;
                        progressIndicator.setIndeterminate(false);
                        progressIndicator.setMax(summary.results.size());
                        progressIndicator.setProgress(summary.results.size());

                        boolean hasWorking = summary.firstOkHost != null;
                        if (hasWorking) {
                            tvProgressText.setText(context.getString(R.string.iptv_mirror_probe_done, summary.results.size()));
                            layoutAllFail.setVisibility(View.GONE);
                            btnUseWorking.setEnabled(true);
                            if (adapter.getSelectedIndex() < 0) {
                                for (int i = 0; i < items.size(); i++) {
                                    if (items.get(i).status == IptvApiClient.MirrorStatus.OK) {
                                        adapter.setSelectedIndex(i);
                                        break;
                                    }
                                }
                            }
                        } else {
                            tvProgressText.setText(R.string.iptv_mirror_none_ok);
                            layoutAllFail.setVisibility(View.VISIBLE);
                            btnUseWorking.setEnabled(false);
                        }
                    }
                });
            }
        };

        btnRetryDiag.setOnClickListener(v -> runProbe.run());

        btnUseWorking.setOnClickListener(v -> {
            IptvApiClient.MirrorHostResult selected = adapter.getSelectedItem();
            if (selected != null && selected.status == IptvApiClient.MirrorStatus.OK) {
                String workingServer = selected.host;
                if (store != null && subId > 0) {
                    store.updateServer(subId, workingServer);
                    store.setMirrors(subId, finalMirrors);
                }
                if (onUpdated != null) {
                    onUpdated.run();
                }
                Toast.makeText(context, context.getString(R.string.iptv_mirror_switched, workingServer), Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } else {
                Toast.makeText(context, R.string.no_working_server_selected, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
        runProbe.run();
    }

    private static class ProbeAdapter extends RecyclerView.Adapter<ProbeAdapter.ViewHolder> {
        private final Context context;
        private final List<IptvApiClient.MirrorHostResult> list;
        private int selectedIndex = -1;
        private OnSelectionChangedListener listener;

        interface OnSelectionChangedListener {
            void onSelectionChanged(IptvApiClient.MirrorHostResult selected);
        }

        ProbeAdapter(Context context, List<IptvApiClient.MirrorHostResult> list) {
            this.context = context;
            this.list = list;
        }

        void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
            this.listener = listener;
        }

        int getSelectedIndex() {
            return selectedIndex;
        }

        void setSelectedIndex(int index) {
            this.selectedIndex = index;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onSelectionChanged(getSelectedItem());
            }
        }

        IptvApiClient.MirrorHostResult getSelectedItem() {
            if (selectedIndex >= 0 && selectedIndex < list.size()) {
                return list.get(selectedIndex);
            }
            return null;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_mirror_probe, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            IptvApiClient.MirrorHostResult item = list.get(position);
            holder.tvHost.setText(item.host);

            if (item.status == IptvApiClient.MirrorStatus.OK) {
                holder.tvStatusBadge.setText(context.getString(R.string.iptv_mirror_status_ok, item.latencyMs));
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_ok);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_ok_text));
                holder.tvLatency.setVisibility(View.VISIBLE);
                holder.tvLatency.setText(item.latencyMs + " ms");
                holder.rbSelected.setVisibility(View.VISIBLE);
                holder.rbSelected.setChecked(position == selectedIndex);
            } else if (item.status == IptvApiClient.MirrorStatus.AUTH_ERROR) {
                holder.tvStatusBadge.setText(R.string.iptv_mirror_status_auth_error);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_auth);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_auth_text));
                holder.tvLatency.setVisibility(View.GONE);
                holder.rbSelected.setVisibility(View.GONE);
            } else {
                holder.tvStatusBadge.setText(R.string.iptv_mirror_status_blocked);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_blocked);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_blocked_text));
                holder.tvLatency.setVisibility(View.GONE);
                holder.rbSelected.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (item.status == IptvApiClient.MirrorStatus.OK) {
                    setSelectedIndex(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final RadioButton rbSelected;
            final TextView tvHost;
            final TextView tvLatency;
            final TextView tvStatusBadge;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                rbSelected = itemView.findViewById(R.id.rb_selected);
                tvHost = itemView.findViewById(R.id.tv_host);
                tvLatency = itemView.findViewById(R.id.tv_latency);
                tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            }
        }
    }
}
