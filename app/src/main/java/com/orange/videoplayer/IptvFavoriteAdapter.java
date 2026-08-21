package com.orange.videoplayer;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class IptvFavoriteAdapter extends RecyclerView.Adapter<IptvFavoriteAdapter.ViewHolder> {

    public interface Listener {
        void onFavoriteClick(JSONObject item);
        void onFavoriteDelete(JSONObject item);
    }

    private final List<JSONObject> items;
    private final Listener listener;

    public IptvFavoriteAdapter(List<JSONObject> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_favorite, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        String name = item.optString("name");
        String url = item.optString("url");

        holder.tvTitle.setText(name);
        String host = null;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception ignored) {
        }
        holder.tvSubtitle.setText(host != null && !host.isEmpty() ? host : url);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteClick(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
