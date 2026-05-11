package com.termux.app.filemanager;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.ViewHolder> {

    private final Context context;
    private List<FileItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private boolean selectionMode = false;
    private OnItemClickListener itemClickListener;
    private OnItemLongClickListener itemLongClickListener;

    public interface OnItemClickListener {
        void onItemClick(FileItem item, int position);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(FileItem item, int position);
    }

    public FileListAdapter(Context context) {
        this.context = context;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.itemLongClickListener = listener;
    }

    public void setItems(List<FileItem> newItems) {
        items.clear();
        items.addAll(newItems);
        sortItems();
        selectedPositions.clear();
        selectionMode = false;
        notifyDataSetChanged();
    }

    public FileItem getItem(int position) {
        return items.get(position);
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean enabled) {
        selectionMode = enabled;
        if (!enabled) selectedPositions.clear();
        notifyDataSetChanged();
    }

    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        if (selectedPositions.isEmpty()) {
            selectionMode = false;
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (int i = 0; i < items.size(); i++) {
            selectedPositions.add(i);
        }
        selectionMode = true;
        notifyDataSetChanged();
    }

    public List<FileItem> getSelectedItems() {
        List<FileItem> result = new ArrayList<>();
        for (int pos : selectedPositions) {
            result.add(items.get(pos));
        }
        return result;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    public void clearSelection() {
        selectedPositions.clear();
        selectionMode = false;
        notifyDataSetChanged();
    }

    private void sortItems() {
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                if (a.isDirectory != b.isDirectory) {
                    return a.isDirectory ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = items.get(position);

        holder.nameView.setText(item.name);

        int iconRes;
        int tintColorRes;

        if (item.isDirectory) {
            iconRes = R.drawable.ic_folder;
            tintColorRes = R.color.file_icon_folder;
            holder.metaView.setText(item.getChildCount() + " items");
        } else {
            String ext = item.name.contains(".") ?
                    item.name.substring(item.name.lastIndexOf(".")).toLowerCase() : "";

            switch (ext) {
                case ".txt": case ".md": case ".sh": case ".py": case ".js": case ".java":
                case ".xml": case ".json": case ".yml": case ".yaml": case ".properties":
                case ".log": case ".csv": case ".cfg": case ".ini": case ".conf":
                case ".css": case ".html": case ".htm": case ".kt": case ".c": case ".h":
                case ".cpp": case ".hpp": case ".rs": case ".go": case ".rb": case ".php":
                case ".ts": case ".tsx": case ".jsx": case ".gradle":
                    iconRes = R.drawable.ic_file_text;
                    tintColorRes = R.color.file_icon_text;
                    break;
                case ".png": case ".jpg": case ".jpeg": case ".gif": case ".bmp":
                case ".webp": case ".svg":
                    iconRes = R.drawable.ic_file_image;
                    tintColorRes = R.color.file_icon_image;
                    break;
                case ".mp4": case ".mkv": case ".avi": case ".mov": case ".webm":
                case ".mp3": case ".wav": case ".ogg": case ".flac": case ".m4a":
                    iconRes = R.drawable.ic_file_video;
                    tintColorRes = R.color.file_icon_video;
                    break;
                case ".zip": case ".tar": case ".gz": case ".bz2": case ".xz":
                case ".apk": case ".jar": case ".7z": case ".rar":
                case ".so": case ".a": case ".o": case ".dex":
                    iconRes = R.drawable.ic_file_binary;
                    tintColorRes = R.color.file_icon_other;
                    break;
                default:
                    iconRes = R.drawable.ic_file;
                    tintColorRes = R.color.file_icon_other;
                    break;
            }
            holder.metaView.setText(FileOperationHelper.formatSize(item.size));
        }

        holder.iconView.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(holder.iconView,
                ColorStateList.valueOf(ContextCompat.getColor(context, tintColorRes)));

        holder.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedPositions.contains(position));

        holder.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(position);
            } else if (itemClickListener != null) {
                itemClickListener.onItemClick(item, position);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                selectionMode = true;
                selectedPositions.add(position);
                notifyDataSetChanged();
            }
            if (itemLongClickListener != null) {
                itemLongClickListener.onItemLongClick(item, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView nameView;
        TextView metaView;
        CheckBox checkBox;

        ViewHolder(View view) {
            super(view);
            iconView = view.findViewById(R.id.file_icon);
            nameView = view.findViewById(R.id.file_name);
            metaView = view.findViewById(R.id.file_meta);
            checkBox = view.findViewById(R.id.file_checkbox);
        }
    }
}
