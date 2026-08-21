package com.orange.videoplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class IptvCategoryAdapter extends RecyclerView.Adapter<IptvCategoryAdapter.ViewHolder> {

    public interface Listener {
        void onCategorySelected(IptvModels.Category category);
    }

    private final List<IptvModels.Category> categories;
    private final Listener listener;
    private int selectedIndex = 0;

    public IptvCategoryAdapter(List<IptvModels.Category> categories, Listener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    public void setSelectedIndex(int index) {
        int oldIndex = this.selectedIndex;
        this.selectedIndex = index;
        notifyItemChanged(oldIndex);
        notifyItemChanged(index);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_iptv_category_chip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IptvModels.Category category = categories.get(position);
        boolean isSelected = (position == selectedIndex);

        String text = category.name;
        if (category.count > 0) {
            text += " (" + category.count + ")";
        }
        holder.tvCategoryName.setText(text);

        if (isSelected) {
            holder.tvCategoryName.setBackgroundResource(R.drawable.bg_hold_indicator);
            holder.tvCategoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent));
        } else {
            holder.tvCategoryName.setBackgroundResource(R.drawable.bg_pill_indicator);
            holder.tvCategoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.dim));
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos != selectedIndex) {
                int prev = selectedIndex;
                selectedIndex = pos;
                notifyItemChanged(prev);
                notifyItemChanged(selectedIndex);
                if (listener != null) listener.onCategorySelected(categories.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCategoryName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryName = (TextView) itemView;
        }
    }
}
