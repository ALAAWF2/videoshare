package com.orange.videoplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    public interface Listener {
        void onDownloadClick(JSONObject item);
        void onDownloadLongClick(JSONObject item);
        void onDownloadDelete(JSONObject item);
        void onDownloadShare(JSONObject item);
    }

    private final List<JSONObject> items = new ArrayList<>();
    private final Listener listener;

    public DownloadAdapter(List<JSONObject> items, Listener listener) {
        if (items != null) {
            this.items.addAll(items);
        }
        this.listener = listener;
    }

    public void updateItems(List<JSONObject> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        Context ctx = holder.itemView.getContext();

        String title = item.optString("title", "فيديو محمل");
        int status = item.optInt("status", DownloadStore.STATUS_PENDING);
        long downloaded = item.optLong("downloadedBytes", 0);
        long total = item.optLong("totalBytes", 0);
        String iconUrl = item.optString("iconUrl");

        holder.tvTitle.setText(title);

        if (status == DownloadStore.STATUS_SUCCESSFUL) {
            String sizeStr = (total > 0) ? (" • " + DownloadHelper.formatFileSize(total)) : "";
            holder.tvStatus.setText(ctx.getString(R.string.download_status_completed) + sizeStr);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_ok_text));
            holder.ivIcon.setImageResource(R.drawable.ic_check);
            holder.progressDownload.setVisibility(View.GONE);
            holder.btnShare.setVisibility(View.VISIBLE);
        } else if (status == DownloadStore.STATUS_FAILED) {
            holder.tvStatus.setText(R.string.download_status_failed);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_blocked_text));
            holder.ivIcon.setImageResource(R.drawable.ic_download);
            holder.progressDownload.setVisibility(View.GONE);
            holder.btnShare.setVisibility(View.GONE);
        } else if (status == DownloadStore.STATUS_PAUSED) {
            String sizeStr = (downloaded > 0) ? (" (" + DownloadHelper.formatFileSize(downloaded) + ")") : "";
            holder.tvStatus.setText(ctx.getString(R.string.download_status_paused) + sizeStr);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_auth_text));
            holder.ivIcon.setImageResource(R.drawable.ic_download);
            holder.progressDownload.setVisibility(View.VISIBLE);
            if (total > 0) {
                holder.progressDownload.setIndeterminate(false);
                holder.progressDownload.setMax((int) (total / 1024));
                holder.progressDownload.setProgress((int) (downloaded / 1024));
            } else {
                holder.progressDownload.setIndeterminate(false);
                holder.progressDownload.setProgress(0);
            }
            holder.btnShare.setVisibility(View.GONE);
        } else {
            // Running or pending
            int pct = (total > 0) ? (int) (downloaded * 100 / total) : 0;
            String text = ctx.getString(R.string.download_status_downloading, pct);
            if (total > 0) {
                text += " (" + DownloadHelper.formatFileSize(downloaded) + " / " + DownloadHelper.formatFileSize(total) + ")";
            } else if (downloaded > 0) {
                text += " (" + DownloadHelper.formatFileSize(downloaded) + ")";
            }
            holder.tvStatus.setText(text);
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
            holder.ivIcon.setImageResource(R.drawable.ic_download);

            holder.progressDownload.setVisibility(View.VISIBLE);
            if (total > 0) {
                holder.progressDownload.setIndeterminate(false);
                holder.progressDownload.setMax((int) (total / 1024));
                holder.progressDownload.setProgress((int) (downloaded / 1024));
            } else {
                holder.progressDownload.setIndeterminate(true);
            }
            holder.btnShare.setVisibility(View.GONE);
        }

        if (iconUrl != null && !iconUrl.isEmpty()) {
            IptvImageLoader.getInstance().load(holder.ivIcon, iconUrl, R.drawable.ic_download);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadClick(item);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onDownloadLongClick(item);
                return true;
            }
            return false;
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadDelete(item);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onDownloadShare(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvStatus;
        final ProgressBar progressDownload;
        final ImageButton btnShare;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvStatus = itemView.findViewById(R.id.tv_status);
            progressDownload = itemView.findViewById(R.id.progress_download);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
