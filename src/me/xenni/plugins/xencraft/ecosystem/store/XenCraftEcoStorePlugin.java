package me.xenni.plugins.xencraft.ecosystem.store;

import me.xenni.plugins.xencraft.ecosystem.MoneySystem;
import me.xenni.plugins.xencraft.ecosystem.ValueStore;
import me.xenni.plugins.xencraft.ecosystem.Wallet;
import me.xenni.plugins.xencraft.ecosystem.XenCraftEcoSystemPlugin;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.StoreTransactionController;
import me.xenni.plugins.xencraft.ecosystem.store.arbiters.factories.StoreTransactionControllerFactory;
import me.xenni.plugins.xencraft.ecosystem.store.bultin.arbiters.factories.UnboundStoreTransactionControllerFactory;
import me.xenni.plugins.xencraft.plugin.GenericXenCraftPlugin;
import me.xenni.plugins.xencraft.util.CommandUtil;
import me.xenni.plugins.xencraft.util.ItemStackUtil;
import me.xenni.plugins.xencraft.util.ModuleUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

import java.io.*;
import java.util.HashMap;
import java.util.logging.Level;

public final class XenCraftEcoStorePlugin extends GenericXenCraftPlugin
{
    private XenCraftEcoSystemPlugin ecoSystemPlugin;
    private int maxStoreInteractionDistance = 3;

    private final static class XenCraftEcoStoreBlockListener extends BlockListener
    {
        private XenCraftEcoStorePlugin ecoStorePlugin;

        public XenCraftEcoStoreBlockListener(XenCraftEcoStorePlugin plugin)
        {
            ecoStorePlugin = plugin;

            PluginManager manager = ecoStorePlugin.getServer().getPluginManager();

            manager.registerEvent(Event.Type.BLOCK_BREAK, this, Event.Priority.Normal, plugin);
        }

        public void onBlockBreak(BlockBreakEvent event)
        {
            for (Location loc : ecoStorePlugin.storeInstances.keySet())
            {
                if (loc.equals(event.getBlock().getLocation()))
                {
                    Player player = event.getPlayer();

                    if (player != null && !player.hasPermission("xencraft.eco.store.create"))
                    {
                        event.setCancelled(true);
                        player.sendMessage("[EcoStore] You do not have permission to do that.");
                        return;
                    }

                    StoreTransactionController controller = ecoStorePlugin.storeInstances.get(loc);
                    controller.OnStoreDeleted();
                    ecoStorePlugin.storeInstances.remove(loc);

                    try
                    {
                        if (!(new File(ecoStorePlugin.getDataFolder().getCanonicalPath() + "/Stores/" + loc.getWorld().getName() + "_" + loc.getX() + "_" + loc.getY() + "_" + loc.getZ() + ".xces").delete()))
                        {
                            throw new IOException("Deletion failed.");
                        }
                    }
                    catch (IOException ex)
                    {
                        ecoStorePlugin.log("Unable to delete store data file.", Level.WARNING);
                        ecoStorePlugin.log("Message: " + ex.getMessage(), 2);
                    }

                    ecoStorePlugin.log("Store at " + loc.toString() + " destroyed by '" + event.getPlayer().getName() + "'.");

                    return;
                }
            }
        }
    }

    private final static class XenCraftEcoStorePlayerListener extends PlayerListener
    {
        private XenCraftEcoStorePlugin ecoStorePlugin;

        public XenCraftEcoStorePlayerListener(XenCraftEcoStorePlugin plugin)
        {
            ecoStorePlugin = plugin;

            PluginManager manager = ecoStorePlugin.getServer().getPluginManager();

            manager.registerEvent(Event.Type.PLAYER_INTERACT, this, Event.Priority.Low, plugin);
        }

        public void onPlayerInteract(PlayerInteractEvent event)
        {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK)
            {
                Player player = event.getPlayer();
                ItemStack items = player.getItemInHand();
                if (items == null || items.getAmount() <= 0)
                {
                    return;
                }

                Location clickloc = event.getClickedBlock().getLocation();
                for (Location storeLoc : ecoStorePlugin.storeInstances.keySet())
                {
                    if (clickloc.equals(storeLoc))
                    {
                        event.setCancelled(true);
                        if (!player.hasPermission("xencraft.eco.store.sell"))
                        {
                            player.sendMessage("You do not have permission to sell to this store.");
                            return;
                        }

                        StoreTransactionController controller = ecoStorePlugin.storeInstances.get(storeLoc);

                        ItemStack item = ItemStackUtil.shallowClone(items, 1);

                        MoneySystem<?> system = controller.getPreferredPaymentSystem();
                        if (system == null)
                        {
                            system = this.ecoStorePlugin.getEcoSystemPlugin().primaryCurrencySystem;
                            if (system == null)
                            {
                                player.sendMessage("[EcoStore] Sale failed: Unable to determine money system. Try using the sell command.");
                                return;
                            }
                        }
                        StoreTransactionController.SaleResult<?> result = controller.sell(item, system);

                        if (!result.isSuccess)
                        {
                            player.sendMessage("[EcoStore] Sale failed: " + result.errorMessage);
                            return;
                        }

                        ItemStackUtil.removeFromInventory(player.getInventory(), item);

                        try
                        {
                            ecoStorePlugin.getEcoSystemPlugin().getWallet("player." + player.getName(), null, null).getValueStoreForMoneySystem(system).addAny(result.salePayment.getValue());
                        }
                        catch (IOException ex)
                        {
                            player.sendMessage("[EcoStore] Sale failed: Unable to access your wallet.");
                            return;
                        }

                        player.sendMessage("[EcoStore] Sale successful.");

                        return;
                    }
                }
            }
        }
    }

    private final static class StoreConfiguration
    {
        public final String name;
        public final StoreTransactionControllerFactory transactionControllerFactory;
        public final Wallet backingWallet;
        private final ConfigurationNode controllerConfig;
        private final XenCraftEcoStorePlugin plugin;

        public StoreConfiguration(String templateName, XenCraftEcoStorePlugin storePlugin) throws Exception
        {
            name = templateName;
            plugin = storePlugin;

            Configuration config = storePlugin.getConfiguration("StoreTemplates/" + name + ".yml");

            String backingWalletName = config.getString("backingwallet");
            if (backingWalletName == null)
            {
                backingWallet = null;
            }
            else
            {
                backingWallet = plugin.ecoSystemPlugin.getWallet(backingWalletName, null, null);
            }

            controllerConfig = config.getNode("controllerconfig");

            String factoryArchive = config.getString("controllerarchive");
            String factoryClass = config.getString("controllerclass");

            if (factoryClass == null)
            {
                transactionControllerFactory = new UnboundStoreTransactionControllerFactory();
            }
            else
            {
                if (factoryArchive == null)
                {
                    transactionControllerFactory = ModuleUtil.loadClass(factoryClass);
                }
                else
                {
                    transactionControllerFactory = ModuleUtil.loadClassFromJar(factoryClass, factoryArchive);
                }
            }

            if (transactionControllerFactory == null)
            {
                throw new Exception("StoreTransactionControllerFactory instance was null.");
            }
        }

        public StoreTransactionController instantiateStoreController(Location location)
        {
            return transactionControllerFactory.getStoreTransactionController(name, controllerConfig, plugin, backingWallet, location);
        }
    }

    private HashMap<String, StoreConfiguration> storeTemplates = new HashMap<String, StoreConfiguration>();
    private HashMap<Location, StoreTransactionController> storeInstances = new HashMap<Location, StoreTransactionController>();
    private XenCraftEcoStoreBlockListener blockListener;
    private XenCraftEcoStorePlayerListener playerListener;

    public XenCraftEcoSystemPlugin getEcoSystemPlugin()
    {
        return ecoSystemPlugin;
    }

    public void onPluginEnable()
    {
        ecoSystemPlugin = XenCraftEcoSystemPlugin.connectToXenCraftPlugin(this, "EcoSystem", XenCraftEcoSystemPlugin.class);
        if (ecoSystemPlugin == null)
        {
            log("Unable to connect to XenCraft EcoSystem!", Level.SEVERE, 1);
            log("Plugin will now be disabled.", 1);

            getServer().getPluginManager().disablePlugin(this);

            return;
        }
        log("Connected to XenCraft EcoSystem: '" + ecoSystemPlugin.getDescription().getVersion() + "'.", 1);

        try
        {
            loadStoreTemplates();
            loadStores();

        }
        catch (Exception ex)
        {
            log("Unable to initialize. Error: " + ex.getMessage(), Level.SEVERE, 1);
            log("Plugin will now be disabled.", 1);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        blockListener = new XenCraftEcoStoreBlockListener(this);
        playerListener = new XenCraftEcoStorePlayerListener(this);
    }
    public void onPluginDisable()
    {
        for(StoreTransactionController controller : storeInstances.values())
        {
            controller.OnStoreUnloaded();
        }

        storeInstances.clear();
        storeTemplates.clear();
    }

    private StoreTransactionController getIntendedController(Player player)
    {
        //player's target
        Block target = player.getTargetBlock(null, maxStoreInteractionDistance);
        boolean searchCandidates = true;
        StoreTransactionController candidate = null;
        Location playerLoc = player.getLocation();

        if (target != null)
        {
            Location targetLoc = target.getLocation();

            for (Location controllerLoc : storeInstances.keySet())
            {
                if (controllerLoc.equals(targetLoc))
                {
                    return storeInstances.get(controllerLoc);
                }

                if (searchCandidates && controllerLoc.getWorld() == player.getWorld() && controllerLoc.distance(playerLoc) <= maxStoreInteractionDistance)
                {
                    if (candidate == null)
                    {
                        candidate = storeInstances.get(controllerLoc);
                    }
                    else
                    {
                        candidate = null;
                        searchCandidates = false;
                    }
                }
            }
        }

        return candidate;
    }

    private boolean cmdexecBuy(Player player, String[] args)
    {
        if (args.length < 1 || args.length > 2)
        {
            return false;
        }

        if (player.hasPermission("xencraft.eco.store.buy"))
        {
            StoreTransactionController controller = getIntendedController(player);
            if (controller == null)
            {
                player.sendMessage("[EcoStore] Unable to determine which store you are trying to trade with.");
                player.sendMessage(" (Try standing closer to the store and/or placing your crosshairs over it.)");
                return true;
            }

            ItemStack items = ItemStackUtil.parse(args[0]);
            if (items == null)
            {
                return false;
            }

            MoneySystem<?> paymentSystem = controller.getPreferredPaymentSystem();
            ValueStore<?> payment;
            if (args.length == 2)
            {
                payment = ecoSystemPlugin.parseRepresentation(args[1]);
                if (payment == null)
                {
                    payment = ecoSystemPlugin.parseRepresentation(args[1], paymentSystem);
                }
            }
            else
            {
                payment = controller.getPrice(items, false, paymentSystem);
            }

            if (payment == null)
            {
                player.sendMessage("[EcoStore] Purchase failed: Payment not recognized.");
                return true;
            }

            ValueStore<?> playerWalletAmount;
            try
            {
                playerWalletAmount = ecoSystemPlugin.getWallet("player." + player.getName(), null, null).getValueStoreForMoneySystem(payment.moneySystem);
                if (playerWalletAmount == null)
                {
                    throw new Exception();
                }
            }
            catch (Exception ex)
            {
                player.sendMessage("[EcoStore] Purchase failed: Unable to access your wallet.");
                return true;
            }

            StoreTransactionController.PurchaseResult result = controller.purchase(items, payment);
            if (!result.isSuccess)
            {
                player.sendMessage("[EcoStore] Purchase failed: " + result.errorMessage);
                return true;
            }

            if (!playerWalletAmount.subtractAny(payment.getValue()))
            {
                player.sendMessage("[EcoStore] Purchase failed: You are unable to pay the required amount.");
                return true;
            }

            player.getInventory().addItem(items);
            player.sendMessage("[EcoStore] Purchase successful.");
        }
        else
        {
            player.sendMessage("[EcoStore] You do not have permission to do that.");
        }

        return true;
    }
    private boolean cmdexecSell(Player player, String[] args)
    {
        if (args.length < 1 || args.length > 2)
        {
            return false;
        }

        if (player.hasPermission("xencraft.eco.store.sell"))
        {
            StoreTransactionController controller = getIntendedController(player);
            if (controller == null)
            {
                player.sendMessage("[EcoStore] Unable to determine which store you are trying to trade with.");
                player.sendMessage(" (Try standing closer to the store and/or placing your crosshairs over it.)");
                return true;
            }

            MoneySystem<?> system = controller.getPreferredPaymentSystem();
            if (args.length == 2)
            {
                system = ecoSystemPlugin.moneySystems.get(args[1]);
            }
            if (system == null)
            {
                player.sendMessage("[EcoStore] Unable to determine the desired money system.");
                return false; //so player can see usage text
            }

            ItemStack items = ItemStackUtil.parse(args[0]);
            if (items == null)
            {
                return false;
            }

            StoreTransactionController.SaleResult<?> result = controller.sell(items, system);

            if (!ItemStackUtil.inventoryContains(player.getInventory(), items))
            {
                player.sendMessage("[EcoStore] You do not have that.");
                return true;
            }

            if (!result.isSuccess)
            {
                player.sendMessage("[EcoStore] Sale failed: " + result.errorMessage);
                return true;
            }

            ItemStackUtil.removeFromInventory(player.getInventory(), items);

            try
            {
                ecoSystemPlugin.getWallet("player." + player.getName(), null, null).getValueStoreForMoneySystem(system).addAny(result.salePayment.getValue());
            }
            catch (IOException ex)
            {
                player.sendMessage("[EcoStore] Sale failed: Unable to access your wallet.");
                return true;
            }

            player.sendMessage("[EcoStore] Sale successful.");
        }
        else
        {
            player.sendMessage("[EcoStore] You do not have permission to do that.");
        }

        return true;
    }
    private boolean cmdexecPrice(Player player, String[] args)
    {
        if (args.length < 1 || args.length > 3)
        {
            return false;
        }

        boolean sell = false;
        if (args[0].equalsIgnoreCase("sell"))
        {
            sell = true;
            if (args.length != 2)
            {
                return false;
            }
        }
        else if (args.length > 1 && args[1].equalsIgnoreCase("sell"))
        {
            sell = true;
            if (args.length != 3)
            {
                return false;
            }
        }

        if ((sell && player.hasPermission("xencraft.eco.store.sell")) || (!sell && player.hasPermission("xencraft.eco.store.buy")))
        {
            StoreTransactionController controller = getIntendedController(player);
            if (controller == null)
            {
                player.sendMessage("[EcoStore] Unable to determine which store you are trying to trade with.");
                player.sendMessage(" (Try standing closer to the store and/or placing your crosshairs over it.)");
                return true;
            }

            MoneySystem<?> priceSystem = controller.getPreferredPaymentSystem();
            if (args.length == 3 || (args.length == 2 && !sell))
            {
                priceSystem = ecoSystemPlugin.moneySystems.get(args[0]);
            }
            if (priceSystem == null)
            {
                player.sendMessage("[EcoStore] Unable to determine the desired money system.");
                return false; //so player can see usage text
            }

            ItemStack items = ItemStackUtil.parse(args[args.length - 1]);
            if (items == null)
            {
                return false;
            }

            ValueStore<?> price = controller.getPrice(items, sell, priceSystem);
            player.sendMessage(price == null ? "[EcoStore] The store cannot do that." : ("[EcoStore] Price: " + price.toString(false)));
        }
        else
        {
             player.sendMessage("[EcoStore] You do not have permission to do that.");
        }

        return true;
    }
    private boolean cmdexecNewStore(Player player, String[] args)
    {
        if (args.length != 1)
        {
            return false;
        }

        if (player.hasPermission("xencraft.eco.store.create"))
        {
            StoreConfiguration template = storeTemplates.get(args[0]);
            if (template == null)
            {
                player.sendMessage("[EcoStore] Template could not be found.");
                return true;
            }

            Block target = player.getTargetBlock(null, maxStoreInteractionDistance);
            if (target == null || (target.getType() != Material.SIGN && target.getType() != Material.WALL_SIGN && target.getType() != Material.SIGN_POST))
            {
                player.sendMessage("[EcoStore] That is not a valid store sign, or you are too far away.");
            }
            Location targetLoc = target.getLocation();

            for (Location loc : storeInstances.keySet())
            {
                if (loc.equals(targetLoc))
                {
                    player.sendMessage("[EcoStore] That is already a store.");
                    return true;
                }
            }

            StoreTransactionController controller = template.instantiateStoreController(targetLoc);
            if (controller == null)
            {
                player.sendMessage("[EcoStore] Unable to create a store there. Try a different template.");
                return true;
            }

            storeInstances.put(targetLoc, controller);

            try
            {
                File storeFile = new File(getDataFolder().getCanonicalPath() + "/Stores/" + targetLoc.getWorld().getName() + "_" + targetLoc.getX() + "_" + targetLoc.getY() + "_" + targetLoc.getZ() + ".xces");
                if (!storeFile.createNewFile())
                {
                    throw new IOException("File creation failed.");
                }

                BufferedWriter wrtr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(storeFile, false)));
                wrtr.write(target.getWorld().getName());
                wrtr.newLine();
                wrtr.write(Integer.toString(target.getX()));
                wrtr.newLine();
                wrtr.write(Integer.toString(target.getY()));
                wrtr.newLine();
                wrtr.write(Integer.toString(target.getZ()));
                wrtr.newLine();
                wrtr.write(args[0]);
                wrtr.newLine();
                wrtr.flush();
                wrtr.close();
            }
            catch (IOException ex)
            {
                player.sendMessage("[EcoStore] WARNING: Unable to create store data file. Store will not exist after reload.");
                log("Unable to create store data file for store @ " + targetLoc.toString() + ".", Level.WARNING);
                log("Message: " + ex.toString(), Level.INFO, 2);
            }

            Sign sign = (Sign)target.getState();
            sign.setLine(0, ChatColor.BLUE + "[Store]");
            sign.update();

            player.sendMessage("[EcoStore] Store created.");
        }
        else
        {
            player.sendMessage("[EcoStore] You do not have permission to do that.");
        }

        return true;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!(sender instanceof Player))
        {
            sender.sendMessage("[EcoStore] This command requires a player context.");
            return true;
        }

        Player player = (Player)sender;

        if (label.equals("buy"))
        {
            return cmdexecBuy(player, CommandUtil.groupArgs(args));
        }
        else if (label.equals("sell"))
        {
            return cmdexecSell(player, CommandUtil.groupArgs(args));
        }
        else if (label.equals("price"))
        {
            return cmdexecPrice(player, CommandUtil.groupArgs(args));
        }
        else if (label.equals("newstore"))
        {
            return cmdexecNewStore(player, CommandUtil.groupArgs(args));
        }

        return false;
    }

    private void loadStoreTemplates() throws Exception
    {
        log("Loading store templates...", 1);
        File templatedir = new File(this.getDataFolder().getCanonicalPath() + "/StoreTemplates/");
        if (!templatedir.exists())
        {
            if (templatedir.mkdirs())
            {
                log("Store template directory did not exist. Created.", 2);
            }
            else
            {
                log("Unable to create store template directory.", Level.SEVERE, 2);
            }
            return;
        }

        for (File file : templatedir.listFiles(
            new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".yml");
                }
            }
        ))
        {
            String name = file.getName();
            name = name.substring(0, name.length() - 4);
            storeTemplates.put(name.toLowerCase(), new StoreConfiguration(name, this));
        }
        log("Loaded " + storeTemplates.size() + " store templates.", 2);
    }
    private void loadStores() throws IOException
    {
        log("Loading stores...", 1);

        File storedir = new File(this.getDataFolder().getCanonicalPath() + "/Stores/");
        if (!storedir.exists())
        {
            if (storedir.mkdirs())
            {
                log("Store directory did not exist. Created.", 2);
            }
            else
            {
                log("Unable to create store directory.", Level.SEVERE, 2);
            }
            return;
        }

        Server server = getServer();

        for (File file : storedir.listFiles(
            new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".xces");
                }
            }
        ))
        {
            BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            try
            {
                String worldName = rdr.readLine();
                World world = server.getWorld(worldName);
                if (world == null)
                {
                    log("Store not loaded: Unable to find world '" + worldName + "'.", Level.WARNING, 2);
                    continue;
                }

                Location loc = new Location(world, Integer.parseInt(rdr.readLine()), Integer.parseInt(rdr.readLine()), Integer.parseInt(rdr.readLine()));

                if (loc.getBlock() == null || (loc.getBlock().getType() != Material.SIGN && loc.getBlock().getType() != Material.WALL_SIGN && loc.getBlock().getType() != Material.SIGN_POST))
                {
                    log("Store not loaded: Sign does not exist.", Level.WARNING, 2);
                    continue;
                }

                String templateName = rdr.readLine();
                StoreConfiguration config = storeTemplates.get(templateName);
                if (config == null)
                {
                    log("Store not loaded: Unable to find template '" + templateName + "'.", Level.WARNING, 2);
                    continue;
                }
                StoreTransactionController controller = config.instantiateStoreController(loc);
                if (controller == null)
                {
                    log("Store not loaded: Controller for template '" + templateName + "' could not be instantiated.");
                    continue;
                }

                controller.OnStoreLoaded();
                storeInstances.put(loc, controller);
            }
            finally
            {
                rdr.close();
            }
        }

        log("Loaded " + storeInstances.size() + " stores.", 2);
    }
}
