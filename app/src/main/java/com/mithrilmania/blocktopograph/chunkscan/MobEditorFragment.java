package com.mithrilmania.blocktopograph.chunkscan;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.WorldActivity;
import com.mithrilmania.blocktopograph.chunk.Chunk;
import com.mithrilmania.blocktopograph.chunk.NBTChunkData;
import com.mithrilmania.blocktopograph.map.Entity;
import com.mithrilmania.blocktopograph.nbt.tags.ByteTag;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.FloatTag;
import com.mithrilmania.blocktopograph.nbt.tags.IntTag;
import com.mithrilmania.blocktopograph.nbt.tags.ListTag;
import com.mithrilmania.blocktopograph.nbt.tags.StringTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists entities (mobs) in a chunk radius around the local player, and lets
 * you toggle each one's "Persistent" flag (so it won't despawn), individually
 * or all at once.
 */
public class MobEditorFragment extends Fragment {

    private static final int CHUNK_RADIUS = 6;

    private final List<MobEntry> entries = new ArrayList<>();
    private RowAdapter adapter;

    private static class MobEntry {
        CompoundTag tag;
        NBTChunkData owningChunkData;
        int x, y, z;
        String name;
        Entity entity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chunk_scan_list, container, false);

        TextView title = root.findViewById(R.id.scan_title);
        TextView hint = root.findViewById(R.id.scan_hint);
        RecyclerView list = root.findViewById(R.id.scan_list);
        Button actionButton = root.findViewById(R.id.scan_action_button);

        title.setText(R.string.mob_editor_title);
        hint.setText(R.string.mob_editor_hint);
        actionButton.setText(R.string.mob_editor_apply_all);

        try {
            if (getContext() != null) Entity.loadEntityBitmaps(getContext().getAssets());
        } catch (Exception ignored) {
        }

        loadNearbyEntities();

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RowAdapter();
        list.setAdapter(adapter);

        actionButton.setOnClickListener(v -> {
            for (MobEntry e : entries) setPersistent(e, true);
            adapter.notifyDataSetChanged();
            saveAll();
        });
        return root;
    }

    private void loadNearbyEntities() {
        entries.clear();
        if (!(getActivity() instanceof WorldActivity)) return;
        WorldActivity activity = (WorldActivity) getActivity();
        ChunkScanUtil.PlayerLocation loc = ChunkScanUtil.getLocalPlayerLocation(activity);
        if (loc == null) return;

        for (int[] cc : ChunkScanUtil.chunksAround(loc.chunkX, loc.chunkZ, CHUNK_RADIUS)) {
            try {
                Chunk chunk = activity.getWorld().getWorldData().getChunk(cc[0], cc[1], loc.dimension);
                if (chunk == null) continue;
                NBTChunkData entityData = chunk.getEntity();
                if (entityData == null) continue;
                entityData.load();
                if (entityData.tags == null) continue;

                for (Tag tag : entityData.tags) {
                    if (!(tag instanceof CompoundTag)) continue;
                    CompoundTag compound = (CompoundTag) tag;

                    Entity entity = null;
                    Tag idTag = compound.getChildTagByKey("id");
                    if (idTag instanceof IntTag) {
                        Integer id = ((IntTag) idTag).getValue();
                        if (id != null) entity = Entity.getEntity(id);
                    }
                    if (entity == null || entity == Entity.UNKNOWN) {
                        Tag idenTag = compound.getChildTagByKey("identifier");
                        if (idenTag instanceof StringTag) {
                            String identifier = ((StringTag) idenTag).getValue();
                            if (identifier != null) entity = Entity.getEntity(identifier);
                        }
                    }
                    if (entity == null) entity = Entity.UNKNOWN;

                    Tag posTag = compound.getChildTagByKey("Pos");
                    if (!(posTag instanceof ListTag)) continue;
                    List<Tag> pos = ((ListTag) posTag).getValue();
                    if (pos.size() < 3) continue;

                    MobEntry entry = new MobEntry();
                    entry.tag = compound;
                    entry.owningChunkData = entityData;
                    entry.x = Math.round(((FloatTag) pos.get(0)).getValue());
                    entry.y = Math.round(((FloatTag) pos.get(1)).getValue());
                    entry.z = Math.round(((FloatTag) pos.get(2)).getValue());
                    entry.entity = entity;
                    entry.name = entity.displayName;
                    entries.add(entry);
                }
            } catch (Exception ignored) {
                // Chunk not present / unreadable -- just skip it.
            }
        }
    }

    private boolean isPersistent(MobEntry e) {
        Tag t = e.tag.getChildTagByKey("Persistent");
        if (!(t instanceof ByteTag)) return false;
        Byte v = ((ByteTag) t).getValue();
        return v != null && v != 0;
    }

    private void setPersistent(MobEntry e, boolean value) {
        Tag existing = e.tag.getChildTagByKey("Persistent");
        if (existing instanceof ByteTag) {
            ((ByteTag) existing).setValue((byte) (value ? 1 : 0));
        } else {
            e.tag.getValue().add(new ByteTag("Persistent", (byte) (value ? 1 : 0)));
        }
    }

    private void saveAll() {
        Set<NBTChunkData> touched = new HashSet<>();
        for (MobEntry e : entries) touched.add(e.owningChunkData);
        int failed = 0;
        for (NBTChunkData data : touched) {
            try {
                data.write();
            } catch (Exception e) {
                failed++;
            }
        }
        View content = getView();
        if (content != null) {
            Snackbar.make(content, failed == 0 ? R.string.mob_editor_saved
                    : R.string.inventory_editor_save_failed, Snackbar.LENGTH_SHORT).show();
        }
    }

    private class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scan_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MobEntry entry = entries.get(position);

            ImageView icon = holder.itemView.findViewById(R.id.row_icon);
            Bitmap bmp = entry.entity.getBitmap();
            icon.setImageBitmap(bmp);

            TextView name = holder.itemView.findViewById(R.id.row_name);
            name.setText(entry.name);

            TextView positionView = holder.itemView.findViewById(R.id.row_position);
            positionView.setText(entry.x + ", " + entry.y + ", " + entry.z);

            CheckBox checkBox = holder.itemView.findViewById(R.id.row_checkbox);
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(isPersistent(entry));
            checkBox.setText(R.string.mob_editor_persistent);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                setPersistent(entry, isChecked);
                saveAll();
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
