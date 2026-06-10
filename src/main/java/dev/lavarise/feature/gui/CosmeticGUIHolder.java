package dev.lavarise.feature.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder for the cosmetics selection panel.
 * Deliberately a separate class so the standard {@link LavaRiseGUIHolder}
 * listeners never fire for the cosmetics GUI.
 *
 * @author DeWost
 */
public class CosmeticGUIHolder implements InventoryHolder {

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
