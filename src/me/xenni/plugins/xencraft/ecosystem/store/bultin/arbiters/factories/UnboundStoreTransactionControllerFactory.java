package me.xenni.plugins.xencraft.ecosystem.store.bultin.arbiters.factories;

import me.xenni.plugins.xencraft.ecosystem.Wallet;
import me.xenni.plugins.xencraft.ecosystem.store.XenCraftEcoStorePlugin;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.StoreTransactionController;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.factories.StoreTransactionControllerFactory;
import me.xenni.plugins.xencraft.ecosystem.store.bultin.arbiters.UnboundStoreTransactionController;
import org.bukkit.Location;
import org.bukkit.util.config.ConfigurationNode;

public class UnboundStoreTransactionControllerFactory implements StoreTransactionControllerFactory
{
    public StoreTransactionController getStoreTransactionController(
        String templateName, ConfigurationNode config,
        XenCraftEcoStorePlugin storePlugin, Wallet backingWallet, Location storeLocation
    )
    {
        if (config == null)
        {
            return new UnboundStoreTransactionController(templateName, storePlugin, storeLocation, 1.0f, 1.0f, null);
        }
        else
        {
            return new UnboundStoreTransactionController(
                templateName, storePlugin, storeLocation,
                (float)config.getDouble("markup", 1.0),
                (float)config.getDouble("markdown", 1.0),
                config.getString("logfile")
            );
        }
    }
}
