package dev.lavarise.feature.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker holder for the admin control panel. Deliberately NOT a
 * {@link LavaRiseGUIHolder} so the join/kit/vote GUI listeners (which route on
 * {@code instanceof LavaRiseGUIHolder}) never fire for the admin panel.
 *
 * @author DeWost
 */
public class AdminGUIHolder implements InventoryHolder {

    /** {@code null} = main panel; otherwise the arena a delete-confirm is for. */
    private final String confirmArena;
    private Inventory inventory;

    public AdminGUIHolder(String confirmArena) {
        this.confirmArena = confirmArena;
    }

    public String getConfirmArena() {
        return confirmArena;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
