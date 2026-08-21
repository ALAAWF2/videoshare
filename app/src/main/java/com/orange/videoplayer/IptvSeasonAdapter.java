package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class IptvSeasonAdapter extends RecyclerView.Adapter<IptvSeasonAdapter.ViewHolder> {

    public interface Listener {
        void onSeasonSelected(IptvModels.Season season);
    }

    private final List<IptvModels.Season> seasons;
    private final Listener listener;
    private int selectedIndex = 0;

    public IptvSeasonAdapter(List<IptvModels.Season> seasons, Listener listener) {
        this.seasons = seasons;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_category_chip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IptvModels.Season season = seasons.get(position);
        boolean isSelected = (position == selectedIndex);

        String text = season.name;
        if (season.episodes.size() > 0) {
            text += " (" + season.episodes.size() + ")";
        }
        holder.tvSeasonName.setText(text);

        if (isSelected) {
            holder.tvSeasonName.setBackgroundResource(R.drawable.bg_hold_indicator);
            holder.tvSeasonName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent));
        } else {
            holder.tvSeasonName.setBackgroundResource(R.drawable.bg_pill_indicator);
            holder.tvSeasonName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.dim));
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos != selectedIndex) {
                int prev = selectedIndex;
                selectedIndex = pos;
                notifyItemChanged(prev);
                notifyItemChanged(selectedIndex);
                if (listener != null) listener.onSeasonSelected(seasons.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return seasons.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSeasonName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSeasonName = (TextView) itemView;
        }
    }
}
