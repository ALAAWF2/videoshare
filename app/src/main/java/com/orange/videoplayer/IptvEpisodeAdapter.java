package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class IptvEpisodeAdapter extends RecyclerView.Adapter<IptvEpisodeAdapter.ViewHolder> {

    public interface Listener {
        void onEpisodeClick(IptvModels.Episode episode);
        void onEpisodeLinkClick(IptvModels.Episode episode);
        void onEpisodeDownloadClick(IptvModels.Episode episode);
    }

    private final List<IptvModels.Episode> episodes;
    private final Listener listener;

    public IptvEpisodeAdapter(List<IptvModels.Episode> episodes, Listener listener) {
        this.episodes = episodes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_episode, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IptvModels.Episode ep = episodes.get(position);
        holder.tvEpisodeTitle.setText(ep.title);
        holder.tvEpisodeNum.setText(holder.itemView.getContext().getString(R.string.iptv_episode_number, ep.episodeNum, ep.title));

        IptvStore iptvStore = new IptvStore(holder.itemView.getContext());
        boolean isFav = iptvStore.isFavorite(ep.streamUrl);
        holder.btnFavorite.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        int accentColor = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.accent);
        int dimColor = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.dim);
        holder.btnFavorite.setColorFilter(isFav ? accentColor : dimColor);

        holder.btnFavorite.setOnClickListener(v -> {
            boolean nowFav = iptvStore.isFavorite(ep.streamUrl);
            if (nowFav) {
                iptvStore.removeFavorite(ep.streamUrl);
                holder.btnFavorite.setImageResource(R.drawable.ic_star_outline);
                holder.btnFavorite.setColorFilter(dimColor);
            } else {
                IptvStore.addFavoriteResolved(holder.itemView.getContext(), ep.title, ep.streamUrl);
                holder.btnFavorite.setImageResource(R.drawable.ic_star_filled);
                holder.btnFavorite.setColorFilter(accentColor);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEpisodeClick(ep);
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onEpisodeDownloadClick(ep);
        });

        holder.btnCopyLink.setOnClickListener(v -> {
            if (listener != null) listener.onEpisodeLinkClick(ep);
        });
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEpisodeTitle;
        final TextView tvEpisodeNum;
        final ImageView ivEpisodeIcon;
        final ImageButton btnDownload;
        final ImageButton btnFavorite;
        final ImageButton btnCopyLink;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpisodeTitle = itemView.findViewById(R.id.tv_episode_title);
            tvEpisodeNum = itemView.findViewById(R.id.tv_episode_num);
            ivEpisodeIcon = itemView.findViewById(R.id.iv_episode_icon);
            btnDownload = itemView.findViewById(R.id.btn_download);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            btnCopyLink = itemView.findViewById(R.id.btn_copy_link);
        }
    }
}
