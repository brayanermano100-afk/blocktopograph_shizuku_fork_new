package com.mithrilmania.blocktopograph.inventory;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mithrilmania.blocktopograph.R;
import com.mithrilmania.blocktopograph.chunk.NBTChunkData;
import com.mithrilmania.blocktopograph.nbt.EditableNBT;
import com.mithrilmania.blocktopograph.nbt.InventoryHolder;
import com.mithrilmania.blocktopograph.nbt.tags.ByteTag;
import com.mithrilmania.blocktopograph.nbt.tags.CompoundTag;
import com.mithrilmania.blocktopograph.nbt.tags.ListTag;
import com.mithrilmania.blocktopograph.nbt.tags.ShortTag;
import com.mithrilmania.blocktopograph.nbt.tags.StringTag;
import com.mithrilmania.blocktopograph.nbt.tags.Tag;

import java.util.ArrayList;

/**
 * A friendlier alternative to the raw NBT tree editor: shows a grid of item
 * icons (instead of a plain list of ids) for either a player's inventory or
 * a container's (chest/furnace/etc) items, and lets the person edit each
 * slot's item/count/damage with a small dialog instead of navigating raw NBT.
 */
public class InventoryEditorFragment extends Fragment {

    private EditableNBT editableNBT;

    // Alternate "container" mode (a chest/furnace/etc instead of a player).
    private CompoundTag containerTag;
    private NBTChunkData owningChunkData;
    private String containerTitle;

    private InventoryHolder inventoryHolder;
    private SlotAdapter adapter;

    public void setEditableNBT(EditableNBT editableNBT) {
        this.editableNBT = editableNBT;
    }

    /** Alternate entry point: edit a container's "Items" tag instead of a player. */
    public void setContainer(CompoundTag containerTag, NBTChunkData owningChunkData, String title) {
        this.containerTag = containerTag;
        this.owningChunkData = owningChunkData;
        this.containerTitle = title;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_inventory_editor, container, false);

        TextView title = root.findViewById(R.id.inventory_title);
        RecyclerView grid = root.findViewById(R.id.inventory_grid);

        boolean isContainerMode = containerTag != null;

        if (isContainerMode) {
            title.setText(containerTitle);
            Tag items = containerTag.getChildTagByKey("Items");
            if (!(items instanceof ListTag)) {
                // Container had no item list yet (e.g. freshly placed) -- create one.
                ListTag newList = new ListTag("Items", new ArrayList<>());
                containerTag.getValue().add(newList);
                items = newList;
            }
            inventoryHolder = InventoryHolder.wrap((ListTag) items);
        } else {
            CompoundTag playerTag = findPlayerCompound();
            if (playerTag == null || editableNBT == null) {
                title.setText(R.string.inventory_editor_error_no_player);
                return root;
            }
            title.setText(editableNBT.getRootTitle());
            inventoryHolder = InventoryHolder.readFromPlayer(playerTag);
            if (inventoryHolder == null) {
                title.setText(R.string.inventory_editor_error_no_inventory);
                return root;
            }
        }

        grid.setLayoutManager(new GridLayoutManager(getContext(), 9));
        adapter = new SlotAdapter();
        grid.setAdapter(adapter);

        root.<FloatingActionButton>findViewById(R.id.fab_add_item).setOnClickListener(v -> showAddItemDialog());

        root.<android.widget.Button>findViewById(R.id.button_save_inventory).setOnClickListener(v -> {
            boolean ok;
            if (isContainerMode) {
                ok = false;
                if (owningChunkData != null) {
                    try {
                        owningChunkData.write();
                        ok = true;
                    } catch (Exception e) {
                        ok = false;
                    }
                }
            } else {
                editableNBT.setModified();
                ok = editableNBT.save();
            }
            View content = getView();
            if (content != null) {
                Snackbar.make(content, ok ? R.string.inventory_editor_saved
                        : R.string.inventory_editor_save_failed, Snackbar.LENGTH_SHORT).show();
            }
        });

        return root;
    }

    /** The player's data (from getTags()) is an ArrayList<Tag> at the root; find the CompoundTag in it. */
    @Nullable
    private CompoundTag findPlayerCompound() {
        if (editableNBT == null) return null;
        for (Tag tag : editableNBT.getTags()) {
            if (tag instanceof CompoundTag) return (CompoundTag) tag;
        }
        return null;
    }

    /** The world folder this player's data belongs to (for reading addon textures), or null. */
    @Nullable
    private java.io.File worldFolderOrNull() {
        if (!(getActivity() instanceof com.mithrilmania.blocktopograph.WorldActivity)) return null;
        com.mithrilmania.blocktopograph.World world =
                ((com.mithrilmania.blocktopograph.WorldActivity) getActivity()).getWorld();
        return world == null ? null : world.worldFolder;
    }

    @Nullable
    private Byte readSlot(CompoundTag itemTag) {
        Tag sub = itemTag.getChildTagByKey("Slot");
        if (sub instanceof ByteTag) return ((ByteTag) sub).getValue();
        return null;
    }

    @Nullable
    private InventoryHolder.Item itemForSlot(Byte slot) {
        if (slot == null) return null;
        return inventoryHolder.getItemOfSlot(slot);
    }

    private void showAddItemDialog() {
        showItemDialog(null, nextFreeSlot());
    }

    private byte nextFreeSlot() {
        boolean[] used = new boolean[128];
        for (Tag t : inventoryHolder.getContent().getValue()) {
            if (!(t instanceof CompoundTag)) continue;
            Byte s = readSlot((CompoundTag) t);
            if (s != null && s >= 0 && s < used.length) used[s] = true;
        }
        for (int i = 0; i < 36; i++) if (!used[i]) return (byte) i;
        return 0;
    }

    /**
     * @param existingSlotTag null to add a brand-new item, or the existing raw
     *                        CompoundTag to edit/delete it.
     */
    private void showItemDialog(@Nullable CompoundTag existingSlotTag, byte slotForNew) {
        if (getContext() == null) return;

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        layout.setPadding(pad, pad, pad, pad);

        EditText nameField = new EditText(getContext());
        nameField.setHint(R.string.inventory_editor_field_name);
        EditText countField = new EditText(getContext());
        countField.setHint(R.string.inventory_editor_field_count);
        countField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText damageField = new EditText(getContext());
        damageField.setHint(R.string.inventory_editor_field_damage);
        damageField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);

        String existingName = null;
        int existingCount = 1;
        int existingDamage = 0;
        if (existingSlotTag != null) {
            Byte slot = readSlot(existingSlotTag);
            InventoryHolder.Item item = itemForSlot(slot);
            if (item != null) {
                existingName = item.getName();
                Byte c = item.getCount();
                if (c != null) existingCount = c;
                Short d = item.getDamage();
                if (d != null) existingDamage = d;
            }
        }
        nameField.setText(existingName != null ? existingName : "minecraft:");
        countField.setText(String.valueOf(existingCount));
        damageField.setText(String.valueOf(existingDamage));

        layout.addView(labeled(R.string.inventory_editor_field_name, nameField));
        layout.addView(labeled(R.string.inventory_editor_field_count, countField));
        layout.addView(labeled(R.string.inventory_editor_field_damage, damageField));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(existingSlotTag != null ? R.string.inventory_editor_edit_item
                        : R.string.inventory_editor_add_item)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = nameField.getText().toString().trim();
                    int count = parseIntOr(countField.getText().toString(), 1);
                    int damage = parseIntOr(damageField.getText().toString(), 0);

                    if (existingSlotTag != null) {
                        applyEdit(existingSlotTag, name, count, damage);
                    } else {
                        addNewItem(slotForNew, name, count, damage);
                    }
                    adapter.notifyDataSetChanged();
                });

        if (existingSlotTag != null) {
            builder.setNeutralButton(R.string.inventory_editor_delete_item, (dialog, which) -> {
                inventoryHolder.getContent().getValue().remove(existingSlotTag);
                adapter.notifyDataSetChanged();
            });
        }

        builder.show();
    }

    private View labeled(int labelRes, View field) {
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 12, 0, 0);
        wrap.addView(field);
        return wrap;
    }

    private int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private void applyEdit(CompoundTag itemTag, String name, int count, int damage) {
        setOrReplaceString(itemTag, "Name", name);
        setOrReplaceByte(itemTag, "Count", (byte) count);
        setOrReplaceShort(itemTag, "Damage", (short) damage);
    }

    private void addNewItem(byte slot, String name, int count, int damage) {
        ArrayList<Tag> subs = new ArrayList<>(4);
        CompoundTag newItem = new CompoundTag("", subs);
        subs.add(new ByteTag("Slot", slot));
        subs.add(new ByteTag("Count", (byte) count));
        subs.add(new ShortTag("Damage", (short) damage));
        subs.add(new StringTag("Name", name));
        inventoryHolder.getContent().getValue().add(newItem);
    }

    private void setOrReplaceString(CompoundTag tag, String key, String value) {
        Tag existing = tag.getChildTagByKey(key);
        if (existing instanceof StringTag) {
            ((StringTag) existing).setValue(value);
        } else {
            tag.getValue().add(new StringTag(key, value));
        }
    }

    private void setOrReplaceByte(CompoundTag tag, String key, byte value) {
        Tag existing = tag.getChildTagByKey(key);
        if (existing instanceof ByteTag) {
            ((ByteTag) existing).setValue(value);
        } else {
            tag.getValue().add(new ByteTag(key, value));
        }
    }

    private void setOrReplaceShort(CompoundTag tag, String key, short value) {
        Tag existing = tag.getChildTagByKey(key);
        if (existing instanceof ShortTag) {
            ((ShortTag) existing).setValue(value);
        } else {
            tag.getValue().add(new ShortTag(key, value));
        }
    }

    private class SlotAdapter extends RecyclerView.Adapter<SlotAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_inventory_slot, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CompoundTag itemTag = rawTagAt(position);
            if (itemTag == null) return;
            Byte slot = readSlot(itemTag);
            InventoryHolder.Item item = itemForSlot(slot);
            if (item == null) return;

            Bitmap icon = getContext() == null ? null
                    : ItemIconResolver.resolveIcon(getContext(), item, worldFolderOrNull());
            ImageView iconView = holder.itemView.findViewById(R.id.slot_icon);
            if (icon != null) {
                iconView.setImageBitmap(icon);
            } else {
                iconView.setImageResource(android.R.drawable.ic_menu_help);
            }

            Byte count = item.getCount();
            TextView countView = holder.itemView.findViewById(R.id.slot_count);
            countView.setText(count == null ? "" : String.valueOf(count));

            TextView slotView = holder.itemView.findViewById(R.id.slot_number);
            slotView.setText(slot == null ? "" : String.valueOf(slot));

            holder.itemView.setContentDescription(ItemIconResolver.resolveDisplayName(item));
            holder.itemView.setOnClickListener(v -> showItemDialog(itemTag, (byte) 0));
        }

        @Nullable
        private CompoundTag rawTagAt(int position) {
            int i = 0;
            for (Tag t : inventoryHolder.getContent().getValue()) {
                if (!(t instanceof CompoundTag)) continue;
                if (i == position) return (CompoundTag) t;
                i++;
            }
            return null;
        }

        @Override
        public int getItemCount() {
            if (inventoryHolder == null) return 0;
            int count = 0;
            for (Tag t : inventoryHolder.getContent().getValue()) {
                if (t instanceof CompoundTag) count++;
            }
            return count;
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
