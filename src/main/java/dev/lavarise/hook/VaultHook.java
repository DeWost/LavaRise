package dev.lavarise.hook;

import dev.lavarise.core.LavaRisePlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Optional, soft Vault economy integration.
 * <p>
 * All references to {@code net.milkbowl.*} are confined to this class and only
 * touched after a {@code getPlugin("Vault") != null} guard, so servers without
 * Vault never hit a {@code NoClassDefFoundError} — mirroring the soft-hook
 * pattern used by {@link PapiExpansion}.
 * </p>
 *
 * @author DeWost
 */
public final class VaultHook {

    private final LavaRisePlugin plugin;
    private Economy economy;

    public VaultHook(LavaRisePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolve the economy provider. Returns true only if Vault and an economy
     * implementation are both present.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    /**
     * Deposit {@code amount} into the player's balance. No-op if Vault is absent.
     *
     * @return true if the deposit succeeded.
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || player == null || amount <= 0) return false;
        try {
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault deposit failed: " + t.getMessage());
            return false;
        }
    }
}
