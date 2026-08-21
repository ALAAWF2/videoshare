package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IptvItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_LIST = 1;
    public static final int VIEW_TYPE_GRID = 2;

    public interface Listener {
        void onItemClick(IptvModels.Item item);
    }

    public interface LongClickListener {
        boolean onItemLongClick(IptvModels.Item item);
    }

    private final List<IptvModels.Item> allItems;
    private final List<IptvModels.Item> displayedItems;
    private final Listener listener;
    private final LongClickListener longClickListener;
    private boolean isGridMode = false;

    public IptvItemAdapter(List<IptvModels.Item> items, Listener listener) {
        this(items, listener, null);
    }

    public IptvItemAdapter(List<IptvModels.Item> items, Listener listener, LongClickListener longClickListener) {
        this.allItems = new ArrayList<>(items);
        this.displayedItems = new ArrayList<>(items);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setGridMode(boolean gridMode) {
        if (this.isGridMode != gridMode) {
            this.isGridMode = gridMode;
            notifyDataSetChanged();
        }
    }

    public boolean isGridMode() {
        return isGridMode;
    }

    public void updateData(List<IptvModels.Item> newItems) {
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
            for (IptvModels.Item item : allItems) {
                if (item.name != null && item.name.toLowerCase(Locale.getDefault()).contains(lower)) {
                    displayedItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isGridMode ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GRID) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_poster, parent, false);
            return new PosterViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_item, parent, false);
            return new ListViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        IptvModels.Item item = displayedItems.get(position);

        int placeholderRes = R.drawable.ic_tv;
        if ("vod".equalsIgnoreCase(item.type) || "movie".equalsIgnoreCase(item.type)) {
            placeholderRes = R.drawable.ic_movie;
        } else if ("series".equalsIgnoreCase(item.type)) {
            placeholderRes = R.drawable.ic_series;
        }

        if (rawHolder instanceof PosterViewHolder) {
            PosterViewHolder holder = (PosterViewHolder) rawHolder;
            holder.tvTitle.setText(item.name);

            if (item.rating != null && !item.rating.trim().isEmpty() && !"0".equals(item.rating)) {
                holder.tvRating.setText("★ " + item.rating.trim());
                holder.tvRating.setVisibility(View.VISIBLE);
            } else {
                holder.tvRating.setVisibility(View.GONE);
            }

            if (item.year != null && !item.year.trim().isEmpty()) {
                holder.tvYear.setText(item.year.trim());
                holder.tvYear.setVisibility(View.VISIBLE);
            } else {
                holder.tvYear.setVisibility(View.GONE);
            }

            IptvImageLoader.getInstance().load(holder.ivPoster, item.iconUrl, placeholderRes);

            boolean hasStreamUrl = item.streamUrl != null && !item.streamUrl.trim().isEmpty() && !"series".equalsIgnoreCase(item.type);
            if (hasStreamUrl) {
                IptvStore iptvStore = new IptvStore(holder.itemView.getContext());
                boolean isFav = iptvStore.isFavorite(item.streamUrl);
                holder.btnFavorite.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                int accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent);
                int dimColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.dim);
                holder.btnFavorite.setColorFilter(isFav ? accentColor : dimColor);
                holder.btnFavorite.setVisibility(isFav ? View.VISIBLE : View.GONE);
            } else {
                holder.btnFavorite.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    return longClickListener.onItemLongClick(item);
                }
                return false;
            });

        } else if (rawHolder instanceof ListViewHolder) {
            ListViewHolder holder = (ListViewHolder) rawHolder;
            holder.tvTitle.setText(item.name);

            if ("series".equalsIgnoreCase(item.type)) {
                holder.ivActionArrow.setImageResource(R.drawable.ic_fast_forward);
                holder.tvSubtitle.setVisibility(View.GONE);
            } else {
                holder.ivActionArrow.setImageResource(R.drawable.ic_play);
                if (item.num > 0 && "live".equalsIgnoreCase(item.type)) {
                    holder.tvSubtitle.setText(String.format(Locale.US, "#%d", item.num));
                    holder.tvSubtitle.setVisibility(View.VISIBLE);
                } else if (item.year != null && !item.year.isEmpty()) {
                    holder.tvSubtitle.setText(item.year);
                    holder.tvSubtitle.setVisibility(View.VISIBLE);
                } else {
                    holder.tvSubtitle.setVisibility(View.GONE);
                }
            }

            IptvImageLoader.getInstance().load(holder.ivIcon, item.iconUrl, placeholderRes);

            boolean hasStreamUrl = item.streamUrl != null && !item.streamUrl.trim().isEmpty() && !"series".equalsIgnoreCase(item.type);
            if (hasStreamUrl) {
                holder.btnFavorite.setVisibility(View.VISIBLE);
                IptvStore iptvStore = new IptvStore(holder.itemView.getContext());
                boolean isFav = iptvStore.isFavorite(item.streamUrl);
                holder.btnFavorite.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                int accentColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent);
                int dimColor = ContextCompat.getColor(holder.itemView.getContext(), R.color.dim);
                holder.btnFavorite.setColorFilter(isFav ? accentColor : dimColor);

                holder.btnFavorite.setOnClickListener(v -> {
                    boolean nowFav = iptvStore.isFavorite(item.streamUrl);
                    if (nowFav) {
                        iptvStore.removeFavorite(item.streamUrl);
                        holder.btnFavorite.setImageResource(R.drawable.ic_star_outline);
                        holder.btnFavorite.setColorFilter(dimColor);
                    } else {
                        IptvStore.addFavoriteResolved(holder.itemView.getContext(), item.name, item.streamUrl);
                        holder.btnFavorite.setImageResource(R.drawable.ic_star_filled);
                        holder.btnFavorite.setColorFilter(accentColor);
                    }
                });
            } else {
                holder.btnFavorite.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    return longClickListener.onItemLongClick(item);
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ListViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final ImageButton btnFavorite;
        final ImageView ivActionArrow;

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            ivActionArrow = itemView.findViewById(R.id.iv_action_arrow);
        }
    }

    static class PosterViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivPoster;
        final TextView tvTitle;
        final TextView tvRating;
        final TextView tvYear;
        final ImageButton btnFavorite;

        PosterViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.iv_poster);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvYear = itemView.findViewById(R.id.tv_year);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
        }
    }
}
