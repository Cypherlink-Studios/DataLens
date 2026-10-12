package dev.darkblade.datalens.adapter;

import de.tr7zw.changeme.nbtapi.NBTType;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import dev.darkblade.datalens.adapter.versioned.Paper121Adapter;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Paper121AdapterTest {

    private Paper121Adapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Paper121Adapter();
    }

    @Test
    void extractComponents_readsNestedComponentsAndCustomData() {
        ReadableNBT rootNbt = mock(ReadableNBT.class);
        ReadableNBT compTag = mock(ReadableNBT.class);
        ReadableNBT customDataTag = mock(ReadableNBT.class);

        when(rootNbt.hasTag("components")).thenReturn(true);
        when(rootNbt.getCompound("components")).thenReturn(compTag);

        when(compTag.getKeys()).thenReturn(Set.of("minecraft:item_model", "minecraft:damage", "minecraft:custom_data"));
        when(compTag.getType("minecraft:item_model")).thenReturn(NBTType.NBTTagString);
        when(compTag.getString("minecraft:item_model")).thenReturn("craftlab:weapons/katana");

        when(compTag.getType("minecraft:damage")).thenReturn(NBTType.NBTTagInt);
        when(compTag.getInteger("minecraft:damage")).thenReturn(15);

        when(compTag.getType("minecraft:custom_data")).thenReturn(NBTType.NBTTagCompound);
        when(compTag.getCompound("minecraft:custom_data")).thenReturn(customDataTag);

        when(customDataTag.getKeys()).thenReturn(Set.of("myplugin:mana", "level"));
        when(customDataTag.getType("myplugin:mana")).thenReturn(NBTType.NBTTagInt);
        when(customDataTag.getInteger("myplugin:mana")).thenReturn(100);
        when(customDataTag.getType("level")).thenReturn(NBTType.NBTTagInt);
        when(customDataTag.getInteger("level")).thenReturn(5);

        DataNode components = adapter.extractComponents(rootNbt);

        assertEquals("components", components.getKey());
        assertEquals(DataType.COMPOUND, components.getType());

        DataNode modelNode = components.getChild("minecraft:item_model").orElse(null);
        assertNotNull(modelNode);
        assertEquals(DataType.STRING, modelNode.getType());
        assertEquals("craftlab:weapons/katana", modelNode.getValue());

        DataNode damageNode = components.getChild("minecraft:damage").orElse(null);
        assertNotNull(damageNode);
        assertEquals(DataType.INT, damageNode.getType());
        assertEquals(15, damageNode.getValue());

        DataNode customData = components.getChild("minecraft:custom_data").orElse(null);
        assertNotNull(customData);
        assertEquals(DataType.COMPOUND, customData.getType());

        DataNode manaNode = customData.getChild("myplugin:mana").orElse(null);
        assertNotNull(manaNode);
        assertEquals(100, manaNode.getValue());
    }

    @Test
    void writeComponents_persistsDataNodeMutationsToNbt() {
        ReadWriteNBT targetNbt = mock(ReadWriteNBT.class);
        ReadWriteNBT compTarget = mock(ReadWriteNBT.class);
        ReadWriteNBT customDataTarget = mock(ReadWriteNBT.class);

        when(targetNbt.getOrCreateCompound("components")).thenReturn(compTarget);
        when(compTarget.getOrCreateCompound("minecraft:custom_data")).thenReturn(customDataTarget);

        DataNode components = DataNode.ofCompound("components");
        components.addChild(DataNode.ofPrimitive("minecraft:item_model", DataType.STRING, "craftlab:armor/crown"));
        components.addChild(DataNode.ofPrimitive("minecraft:damage", DataType.INT, 0));

        DataNode customData = DataNode.ofCompound("minecraft:custom_data");
        customData.addChild(DataNode.ofPrimitive("craftlab:rarity", DataType.STRING, "LEGENDARY"));
        components.addChild(customData);

        adapter.writeComponents(components, targetNbt);

        verify(targetNbt).getOrCreateCompound("components");
        verify(compTarget).setString("minecraft:item_model", "craftlab:armor/crown");
        verify(compTarget).setInteger("minecraft:damage", 0);
        verify(customDataTarget).setString("craftlab:rarity", "LEGENDARY");
    }

    @Test
    void extractComponents_returnsEmptyCompound_whenNoComponents() {
        ReadableNBT emptyNbt = mock(ReadableNBT.class);
        when(emptyNbt.hasTag("components")).thenReturn(false);
        when(emptyNbt.getKeys()).thenReturn(Set.of());

        DataNode components = adapter.extractComponents(emptyNbt);

        assertEquals("components", components.getKey());
        assertEquals(0, components.childCount());
    }
}
