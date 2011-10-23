package me.xenni.plugins.xencraft.ecosystem.store.bultin.arbiters;

import me.xenni.plugins.xencraft.ecosystem.CurrencySystem;
import me.xenni.plugins.xencraft.ecosystem.MoneySystem;
import me.xenni.plugins.xencraft.ecosystem.ValueStore;
import me.xenni.plugins.xencraft.ecosystem.XenCraftEcoSystemPlugin;
import me.xenni.plugins.xencraft.ecosystem.store.XenCraftEcoStorePlugin;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.StoreTransactionController;
import me.xenni.plugins.xencraft.util.ItemStackUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;

import java.io.*;
import java.util.Date;

public class UnboundStoreTransactionController extends StoreTransactionController
{
    private float sellMarkupFactor;
    private float buyMarkdownFactor;
    private BufferedWriter logfile = null;
    private final XenCraftEcoSystemPlugin ecoSystemPlugin;

    public UnboundStoreTransactionController(String template, XenCraftEcoStorePlugin plugin, Location location, float markup, float markdown, String logfilename)
    {
        super(template, plugin, location);

        sellMarkupFactor = markup;
        buyMarkdownFactor = markdown;
        ecoSystemPlugin = storePlugin.getEcoSystemPlugin();

        if (logfilename != null)
        {
            try
            {
                logfile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logfilename)));
            }
            catch (FileNotFoundException ex)
            {
                //nothing we can do
            }
        }
    }

    private void log(String str)
    {
        if (logfile != null)
        {
            try
            {
                logfile.write(str);
                logfile.newLine();
                logfile.flush();
            }
            catch (IOException ex)
            {
                //nothing we can do
            }
        }
    }
    private void finishLogging()
    {
        if (logfile != null)
        {
            try
            {
                logfile.flush();
                logfile.close();
            }
            catch (IOException ex)
            {
                //nothing we can do
            }
        }
    }

    public <V> ValueStore<V> getPrice(ItemStack items, boolean isSellingToStore, MoneySystem<V> paymentSystem)
    {
        if (!(paymentSystem instanceof CurrencySystem))
        {
            return null;
        }

        Float fprice = ecoSystemPlugin.appraiser.appraise(items);
        if (fprice == null)
        {
            return null;
        }

        fprice *= (isSellingToStore ? buyMarkdownFactor : sellMarkupFactor);
        if (fprice <= 0)
        {
            return null;
        }

        return ecoSystemPlugin.convertToValueStore(fprice, ecoSystemPlugin.primaryCurrencySystem, paymentSystem);
    }
    public <V> SaleResult<V> sell(ItemStack items, MoneySystem<V> paymentSystem)
    {
        ValueStore<V> price = getPrice(items, true, paymentSystem);
        if (price == null)
        {
            return new SaleResult<V>("This store does not want those items.");
        }
        else
        {
            log("<$ purchase approved: " + price.toString(false) + " for " + ItemStackUtil.toString(items));
            return new SaleResult<V>(price);
        }
    }
    public PurchaseResult purchase(ItemStack items, ValueStore<?> payment)
    {
        ValueStore<?> price = getPrice(items, false, payment.moneySystem);
        if (price == null)
        {
            return new PurchaseResult("This store does not want to sell those items or will not accept that money.");
        }
        if (price.compareAny(payment) != 0)
        {
            return new PurchaseResult("That payment is not valid.");
        }

        log("$> sale approved: " + price.toString(false) + " for " + ItemStackUtil.toString(items));
        return new PurchaseResult();
    }

    public void OnStoreCreated()
    {
        log("######## CREATED " + (new Date()).toString() + " @ " + storeLocation.toString() + " ########");
    }
    public void OnStoreLoaded()
    {
        log("-------- LOADED " + (new Date()).toString() + " --------");
    }
    public void OnStoreUnloaded()
    {
        log("-------- UNLOADED " + (new Date()).toString() + " --------");
        finishLogging();
    }
    public void OnStoreDeleted()
    {
        log("######## DELETED " + (new Date()).toString() + " ########");
        finishLogging();
    }
}
