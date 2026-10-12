package dev.darkblade.datalens.adapter.versioned;

import de.tr7zw.changeme.nbtapi.NBTItem;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.InspectableObject;
import dev.darkblade.datalens.model.InspectableType;
import dev.darkblade.datalens.util.NbtUtil;
import dev.darkblade.datalens.util.PdcUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * Adapter implementation for Paper 1.21.x.
 * Adds support for Minecraft 1.20.5+ and 1.21.2-1.21.4+ Item Data Components.
 */
public class Paper121Adapter extends Paper120Adapter {

    @Override
    public String getSupportedVersion() { return "1.21"; }

    @Override
    public DataNode readItemData(ItemStack item) {
        DataNode root = super.readItemData(item);

        // Read PDC if available on ItemMeta and not already present
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PersistentDataHolder holder && root.getChild("pdc").isEmpty()) {
                root.addChild(PdcUtil.readPdc(holder.getPersistentDataContainer()));
            }
        }

        // Read Data Components via NBT-API
        try {
            NBTItem nbtItem = new NBTItem(item);
            DataNode componentsNode = extractComponents(nbtItem);
            root.addChild(componentsNode);
        } catch (Exception e) {
            root.addChild(DataNode.ofCompound("components"));
        }

        return root;
    }

    /**
     * Extracts components from a readable NBT source into a DataNode compound.
     */
    public DataNode extractComponents(ReadableNBT nbt) {
        DataNode componentsNode = DataNode.ofCompound("components");
        if (nbt == null) return componentsNode;

        if (nbt.hasTag("components")) {
            ReadableNBT compTag = nbt.getCompound("components");
            if (compTag != null) {
                return NbtUtil.readCompound("components", compTag);
            }
        } else {
            // Check for direct namespaced component keys
            for (String key : nbt.getKeys()) {
                if (key.contains(":") || key.startsWith("minecraft:")) {
                    try {
                        ReadableNBT subComp = nbt.getCompound(key);
                        if (subComp != null) {
                            componentsNode.addChild(NbtUtil.readCompound(key, subComp));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return componentsNode;
    }

    /**
     * Applies components from a DataNode to a target read/write NBT compound.
     */
    public void writeComponents(DataNode componentsNode, ReadWriteNBT targetNbt) {
        if (componentsNode == null || targetNbt == null) return;
        ReadWriteNBT compTarget = targetNbt.getOrCreateCompound("components");
        NbtUtil.writeCompound(componentsNode, compTarget);
    }

    @Override
    public void writeData(InspectableObject object, DataNode node) {
        if (object.getType() != InspectableType.ITEM) {
            super.writeData(object, node);
            return;
        }

        if (!(object.getLiveReference() instanceof ItemStack item)) {
            return;
        }

        // 1. Standard Bukkit ItemMeta & standard fields
        super.writeData(object, node);

        // 2. Data Components
        node.getChild("components").ifPresent(componentsNode -> {
            try {
                NBTItem nbtItem = new NBTItem(item);
                writeComponents(componentsNode, nbtItem);
                nbtItem.applyNBT(item);
            } catch (Exception ignored) {}
        });

        // 3. PDC updates
        node.getChild("pdc").ifPresent(pdcNode -> {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof PersistentDataHolder holder) {
                    for (DataNode child : pdcNode.getChildren()) {
                        String[] parts = child.getKey().split(":", 2);
                        if (parts.length == 2) {
                            NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                            PdcUtil.writeNode(holder.getPersistentDataContainer(), key, child);
                        }
                    }
                    item.setItemMeta(meta);
                }
            }
        });
    }
}
