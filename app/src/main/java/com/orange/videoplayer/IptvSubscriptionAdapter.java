package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class IptvSubscriptionAdapter extends RecyclerView.Adapter<IptvSubscriptionAdapter.ViewHolder> {

    public interface Listener {
        void onOpen(JSONObject subscription);
        void onDelete(JSONObject subscription);
        void onLongClick(JSONObject subscription);
    }

    private final List<JSONObject> items;
    private final Listener listener;

    public IptvSubscriptionAdapter(List<JSONObject> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_subscription, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        String name = item.optString("name", "اشتراك");
        String type = item.optString("type", IptvStore.TYPE_XTREAM);
        String server = item.optString("server", "");
        String url = item.optString("url", "");
        String username = item.optString("username", "");

        holder.tvName.setText(name);

        if (IptvStore.TYPE_M3U.equalsIgnoreCase(type)) {
            holder.tvTypeBadge.setText(R.string.iptv_type_m3u);
            holder.tvServer.setText(url);
            holder.ivTypeIcon.setImageResource(R.drawable.ic_tv);
        } else {
            holder.tvTypeBadge.setText(R.string.iptv_type_xtream);
            String sub = server + (username.isEmpty() ? "" : " (" + username + ")");
            holder.tvServer.setText(sub);
            holder.ivTypeIcon.setImageResource(R.drawable.ic_tv);
        }

        holder.itemRoot.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(item);
        });

        holder.itemRoot.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(item);
                return true;
            }
            return false;
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View itemRoot;
        final ImageView ivTypeIcon;
        final TextView tvName;
        final TextView tvTypeBadge;
        final TextView tvServer;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.item_root);
            ivTypeIcon = itemView.findViewById(R.id.iv_type_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvTypeBadge = itemView.findViewById(R.id.tv_type_badge);
            tvServer = itemView.findViewById(R.id.tv_server);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
