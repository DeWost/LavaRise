package dev.lavarise.feature.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Marker holder for the spectator teleport menu.
 * Deliberately NOT a {@link LavaRiseGUIHolder} so the join/kit/vote GUI
 * listeners never fire for this inventory.
 *
 * @author DeWost
 */
public final class SpectatorMenuHolder implements InventoryHolder {

    /** UUID of the spectator who opened this menu. */
    private final UUID spectatorId;

    private Inventory inventory;

    public SpectatorMenuHolder(UUID spectatorId) {
        this.spectatorId = spectatorId;
    }

    public UUID getSpectatorId() {
        return spectatorId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}
