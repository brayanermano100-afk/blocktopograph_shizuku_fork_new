package com.mithrilmania.blocktopograph.chunkscan;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.WorldActivity;
import com.mithrilmania.blocktopograph.chunk.Chunk;
import com.mithrilmania.blocktopograph.chunk.NBTChunkData;
import com.mithrilmania.blocktopograph.inventory.InventoryEditorFragment;
import com.mithrilmania.blocktopograph.map.TileEntity;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.IntTag;
import com.mithrilmania.blocktopograph.nbt.tags.ListTag;
import com.mithrilmania.blocktopograph.nbt.tags.StringTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists containers (chests, furnaces, etc -- anything with an "Items" tag)
 * in a chunk radius around the local player. Tapping one opens it in the
 * same friendly grid editor used for player inventories.
 */
public class ContainerEditorFragment extends Fragment {

    private static final int CHUNK_RADIUS = 6;

    private final List<ContainerEntry> entries = new ArrayList<>();

    private static class ContainerEntry {
        CompoundTag tag;
        NBTChunkData owningChunkData;
        int x, y, z;
        String id;
        int itemCount;
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

        title.setText(R.string.container_editor_title);
        hint.setText(R.string.container_editor_hint);
        actionButton.setVisibility(View.GONE);

        try {
            if (getContext() != null) TileEntity.loadIcons(getContext().getAssets());
        } catch (Exception ignored) {
        }

        loadNearbyContainers();

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(new RowAdapter());

        return root;
    }

    private void loadNearbyContainers() {
        entries.clear();
        if (!(getActivity() instanceof WorldActivity)) return;
        WorldActivity activity = (WorldActivity) getActivity();
        ChunkScanUtil.PlayerLocation loc = ChunkScanUtil.getLocalPlayerLocation(activity);
        if (loc == null) return;

        for (int[] cc : ChunkScanUtil.chunksAround(loc.chunkX, loc.chunkZ, CHUNK_RADIUS)) {
            try {
                Chunk chunk = activity.getWorld().getWorldData().getChunk(cc[0], cc[1], loc.dimension);
                if (chunk == null) continue;
                NBTChunkData blockEntityData = chunk.getBlockEntity();
                if (blockEntityData == null) continue;
                blockEntityData.load();
                if (blockEntityData.tags == null) continue;

                for (Tag tag : blockEntityData.tags) {
                    if (!(tag instanceof CompoundTag)) continue;
                    CompoundTag compound = (CompoundTag) tag;

                    Tag itemsTag = compound.getChildTagByKey("Items");
                    if (!(itemsTag instanceof ListTag)) continue; // not a container

                    Tag idTag = compound.getChildTagByKey("id");
                    Tag xTag = compound.getChildTagByKey("x");
                    Tag yTag = compound.getChildTagByKey("y");
                    Tag zTag = compound.getChildTagByKey("z");
                    if (!(idTag instanceof StringTag) || !(xTag instanceof IntTag)
                            || !(yTag instanceof IntTag) || !(zTag instanceof IntTag)) continue;

                    ContainerEntry entry = new ContainerEntry();
                    entry.tag = compound;
                    entry.owningChunkData = blockEntityData;
                    entry.id = ((StringTag) idTag).getValue();
                    Integer ex = ((IntTag) xTag).getValue();
                    Integer ey = ((IntTag) yTag).getValue();
                    Integer ez = ((IntTag) zTag).getValue();
                    entry.x = ex == null ? 0 : ex;
                    entry.y = ey == null ? 0 : ey;
                    entry.z = ez == null ? 0 : ez;
                    entry.itemCount = ((ListTag) itemsTag).getValue().size();
                    entries.add(entry);
                }
            } catch (Exception ignored) {
                // Chunk not present / unreadable -- just skip it.
            }
        }
    }

    private void openContainer(ContainerEntry entry) {
        if (!(getActivity() instanceof WorldActivity)) return;
        WorldActivity activity = (WorldActivity) getActivity();

        InventoryEditorFragment fragment = new InventoryEditorFragment();
        String title = (entry.id == null ? "?" : entry.id) + " (" + entry.x + ", " + entry.y + ", " + entry.z + ")";
        fragment.setContainer(entry.tag, entry.owningChunkData, title);

        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.world_content, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
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
            ContainerEntry entry = entries.get(position);

            TileEntity te = entry.id == null ? null : TileEntity.getTileEntity(entry.id);
            ImageView icon = holder.itemView.findViewById(R.id.row_icon);
            Bitmap bmp = te != null ? te.getBitmap() : null;
            if (bmp != null) {
                icon.setImageBitmap(bmp);
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_agenda);
            }

            TextView name = holder.itemView.findViewById(R.id.row_name);
            name.setText((te != null ? te.displayName : entry.id) + " · "
                    + entry.itemCount + " " + getString(R.string.container_editor_items_suffix));

            TextView positionView = holder.itemView.findViewById(R.id.row_position);
            positionView.setText(entry.x + ", " + entry.y + ", " + entry.z);

            holder.itemView.findViewById(R.id.row_checkbox).setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> openContainer(entry));
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
