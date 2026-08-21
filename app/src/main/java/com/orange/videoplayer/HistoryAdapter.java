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
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface Listener {
        void onHistoryClick(JSONObject item);
        void onHistoryDelete(JSONObject item);
    }

    private final List<JSONObject> items;
    private final Listener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.getDefault());

    public HistoryAdapter(List<JSONObject> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        Context ctx = holder.itemView.getContext();

        String title = item.optString("title", "فيديو");
        String url = item.optString("url");
        String iconUrl = item.optString("iconUrl");
        String type = item.optString("type", "vod");
        long pos = item.optLong("pos", 0);
        long dur = item.optLong("dur", 0);
        long ts = item.optLong("ts", 0);

        holder.tvTitle.setText(title);

        String dateStr = (ts > 0) ? dateFormat.format(new Date(ts)) : "";
        String progressStr = "";
        if (dur > 0 && pos > 0) {
            progressStr = " • " + PlayerActivity.formatTime(pos) + " / " + PlayerActivity.formatTime(dur);
            holder.pbProgress.setVisibility(View.VISIBLE);
            holder.pbProgress.setProgress((int) Math.min(1000, pos * 1000 / dur));
        } else {
            holder.pbProgress.setVisibility(View.GONE);
        }

        holder.tvTimeAndDate.setText(ctx.getString(R.string.history_last_watched, dateStr + progressStr));

        int placeholder = R.drawable.ic_movie;
        if ("live".equalsIgnoreCase(type)) {
            placeholder = R.drawable.ic_tv;
        } else if ("series".equalsIgnoreCase(type)) {
            placeholder = R.drawable.ic_series;
        }

        if (iconUrl != null && !iconUrl.isEmpty()) {
            IptvImageLoader.getInstance().load(holder.ivIcon, iconUrl, placeholder);
        } else {
            holder.ivIcon.setImageResource(placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onHistoryClick(item);
        });

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onHistoryClick(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onHistoryDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvTimeAndDate;
        final ProgressBar pbProgress;
        final ImageButton btnPlay;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTimeAndDate = itemView.findViewById(R.id.tv_time_and_date);
            pbProgress = itemView.findViewById(R.id.pb_progress);
            btnPlay = itemView.findViewById(R.id.btn_play);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
