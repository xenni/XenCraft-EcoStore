package me.xenni.plugins.xencraft.ecosystem.store.arbiters.factories;

import me.xenni.plugins.xencraft.ecosystem.Wallet;
import me.xenni.plugins.xencraft.ecosystem.store.XenCraftEcoStorePlugin;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.StoreTransactionController;
import org.bukkit.util.config.ConfigurationNode;
import org.bukkit.Location;

public interface StoreTransactionControllerFactory
{
    public StoreTransactionController getStoreTransactionController(
        String templateName, ConfigurationNode config,
        XenCraftEcoStorePlugin storePlugin, Wallet backingWallet, Location storeLocation
    );
}
