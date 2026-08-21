package com.orange.videoplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocalMediaAdapter extends RecyclerView.Adapter<LocalMediaAdapter.ViewHolder> {

    public interface Listener {
        void onMediaClick(LocalMediaItem item);
        void onMediaShare(LocalMediaItem item);
    }

    private final List<LocalMediaItem> allItems;
    private final List<LocalMediaItem> displayedItems;
    private final Listener listener;

    public LocalMediaAdapter(List<LocalMediaItem> items, Listener listener) {
        this.allItems = new ArrayList<>(items);
        this.displayedItems = new ArrayList<>(items);
        this.listener = listener;
    }

    public void updateData(List<LocalMediaItem> newItems) {
        this.allItems.clear();
        this.allItems.addAll(newItems);
        this.displayedItems.clear();
        this.displayedItems.addAll(newItems);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayedItems.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedItems.addAll(allItems);
        } else {
            String lower = query.toLowerCase(Locale.getDefault()).trim();
            for (LocalMediaItem item : allItems) {
                if (item.title != null && item.title.toLowerCase(Locale.getDefault()).contains(lower)) {
                    displayedItems.add(item);
                } else if (item.folderName != null && item.folderName.toLowerCase(Locale.getDefault()).contains(lower)) {
                    displayedItems.add(item);
                } else if (item.artist != null && item.artist.toLowerCase(Locale.getDefault()).contains(lower)) {
                    displayedItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_media, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocalMediaItem item = displayedItems.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvTitle.setText(item.title);

        // Subtitle info: Folder or Artist + File Size
        StringBuilder info = new StringBuilder();
        if (item.isVideo) {
            if (item.folderName != null && !item.folderName.isEmpty()) {
                info.append("📁 ").append(item.folderName);
            }
        } else {
            if (item.artist != null && !item.artist.isEmpty() && !"<unknown>".equalsIgnoreCase(item.artist)) {
                info.append("🎤 ").append(item.artist);
            } else {
                info.append("🎵 صوت");
            }
        }

        if (item.sizeBytes > 0) {
            if (info.length() > 0) info.append(" • ");
            info.append(DownloadHelper.formatFileSize(item.sizeBytes));
        }
        holder.tvInfo.setText(info.toString());

        // Duration overlay badge
        if (item.durationMs > 0) {
            holder.tvDuration.setText(PlayerActivity.formatTime(item.durationMs));
            holder.tvDuration.setVisibility(View.VISIBLE);
        } else {
            holder.tvDuration.setVisibility(View.GONE);
        }

        // Thumbnail loading
        LocalMediaScanner.loadThumbnail(ctx, item, holder.ivThumbnail);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMediaClick(item);
        });

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onMediaClick(item);
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onMediaShare(item);
        });
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivThumbnail;
        final TextView tvDuration;
        final TextView tvTitle;
        final TextView tvInfo;
        final ImageButton btnShare;
        final ImageButton btnPlay;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvInfo = itemView.findViewById(R.id.tv_info);
            btnShare = itemView.findViewById(R.id.btn_share);
            btnPlay = itemView.findViewById(R.id.btn_play);
        }
    }
}
