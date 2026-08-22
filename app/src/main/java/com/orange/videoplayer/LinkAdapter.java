package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.List;

public class LinkAdapter extends RecyclerView.Adapter<LinkAdapter.VH> {

    public interface Listener {
        void onContinue(JSONObject entry);

        void onRestart(JSONObject entry);

        void onDelete(JSONObject entry);

        void onRename(JSONObject entry);
    }

    private final List<JSONObject> items;
    private final Listener listener;

    public LinkAdapter(List<JSONObject> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static String fmtTime(long ms) {
        if (ms <= 0) return "00:00";
        long s = ms / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_link, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        JSONObject e = items.get(position);
        long p = e.optLong("pos");
        long d = e.optLong("dur");

        h.title.setText(e.optString("name"));
        String u = e.optString("url");
        h.url.setText(u);

        String lower = (e.optString("name") + " " + u).toLowerCase();
        if (h.badge != null) {
            if (lower.contains("4k") || lower.contains("2160")) {
                h.badge.setText("4K");
                h.badge.setVisibility(View.VISIBLE);
            } else if (lower.contains("1080") || lower.contains("fhd")) {
                h.badge.setText("1080p");
                h.badge.setVisibility(View.VISIBLE);
            } else if (lower.contains("m3u8") || lower.contains("live")) {
                h.badge.setText("HLS");
                h.badge.setVisibility(View.VISIBLE);
            } else if (lower.contains("mp4")) {
                h.badge.setText("MP4");
                h.badge.setVisibility(View.VISIBLE);
            } else {
                h.badge.setVisibility(View.GONE);
            }
        }

        boolean done = d > 0 && p >= d - 1000;
        if (done) {
            h.progress.setProgress(1000);
            h.time.setText(R.string.done);
            h.title.setAlpha(0.6f);
        } else if (p > 0 && d > 0) {
            h.progress.setProgress((int) Math.min(1000, p * 1000 / d));
            h.time.setText(fmtTime(p) + " / " + fmtTime(d));
            h.title.setAlpha(1f);
        } else {
            h.progress.setProgress(0);
            h.time.setText(R.string.not_started);
            h.title.setAlpha(1f);
        }

        h.card.setOnClickListener(v -> listener.onContinue(e));
        h.card.setOnLongClickListener(v -> {
            listener.onRename(e);
            return true;
        });
        h.restart.setOnClickListener(v -> listener.onRestart(e));
        h.delete.setOnClickListener(v -> listener.onDelete(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final View card;
        final TextView title;
        final TextView url;
        final TextView time;
        final TextView badge;
        final ProgressBar progress;
        final MaterialButton restart;
        final ImageButton delete;

        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.card);
            title = v.findViewById(R.id.tv_title);
            url = v.findViewById(R.id.tv_url);
            time = v.findViewById(R.id.tv_time);
            badge = v.findViewById(R.id.tv_badge);
            progress = v.findViewById(R.id.pb_pos);
            restart = v.findViewById(R.id.btn_restart);
            delete = v.findViewById(R.id.btn_delete);
        }
    }
}
