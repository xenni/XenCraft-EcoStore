package me.xenni.plugins.xencraft.ecosystem.store.arbiters;

import me.xenni.plugins.xencraft.ecosystem.MoneySystem;
import me.xenni.plugins.xencraft.ecosystem.ValueStore;
import me.xenni.plugins.xencraft.ecosystem.store.XenCraftEcoStorePlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

public abstract class StoreTransactionController
{
    public final String templateName;
    protected final XenCraftEcoStorePlugin storePlugin;
    protected final Location storeLocation;

    public final static class PurchaseResult
    {
        public final boolean isSuccess;
        public final String errorMessage;

        public PurchaseResult()
        {
            isSuccess = true;
            errorMessage = null;
        }
        public PurchaseResult(String message)
        {
            isSuccess = (message == null);
            errorMessage = message;
        }
    }
    public final static class SaleResult<V>
    {
        public final boolean isSuccess;
        public final String errorMessage;
        public final ValueStore<V> salePayment;

        public SaleResult(String message)
        {
            isSuccess = false;
            errorMessage = message;
            salePayment = null;
        }
        public SaleResult(ValueStore<V> payment)
        {
            isSuccess = (payment != null);
            errorMessage = null;
            salePayment = payment;
        }
    }

    public StoreTransactionController(String template, XenCraftEcoStorePlugin plugin, Location location)
    {
        templateName = template;
        storePlugin = plugin;
        storeLocation = location;
    }
    public MoneySystem<?> getPreferredPaymentSystem()
    {
        return storePlugin.getEcoSystemPlugin().primaryCurrencySystem;
    }

    public abstract <V> ValueStore<V> getPrice(ItemStack stack, boolean isSellingToStore, MoneySystem<V> paymentSystem);
    public abstract PurchaseResult purchase(ItemStack stack, ValueStore<?> payment);
    public abstract <V> SaleResult<V> sell(ItemStack stack, MoneySystem<V> paymentSystem);

    public void OnStoreCreated()
    {
    }
    public void OnStoreLoaded()
    {
    }
    public void OnStoreUnloaded()
    {
    }
    public void OnStoreDeleted()
    {
    }
}
