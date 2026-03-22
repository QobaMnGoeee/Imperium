package kz.anarchy.imperium;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;
    private ConfigManager configManager;
    private DataManager dataManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;

        // Config жүктеу
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);

        // Vault экономикасын жүктеу
        setupEconomy();

        // Командаларды тіркеу — FIX #1: рефлексия жоқ, Paper API қолданамыз
        registerCommands();

        // Автоматты сақтау тапсырмасы
        int minutes = getConfig().getInt("settings.auto-save-minutes", 5);
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> dataManager.saveAll(), ticks, ticks);

        getLogger().info("╔══════════════════════════════════╗");
        getLogger().info("║    ImperiumPlugin  ҚОСЫЛДЫ      ║");
        getLogger().info("║  Команда: /" + configManager.getCommandName() + "           ║");
        getLogger().info("╚══════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        getLogger().info("ImperiumPlugin өшірілді. Деректер сақталды.");
    }

    // ── Vault экономикасын іздеу ──
    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault табылмады! Экономика жұмыс істемейді.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
            getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            getLogger().info("Vault экономикасы табылды: " + economy.getName());
        } else {
            getLogger().warning("Vault экономика плагині жоқ (EssentialsX, CMI, т.б. керек).");
        }
    }

    // ── FIX #1: Рефлексия жоқ — Bukkit.getCommandMap() Paper public API ──
    private void registerCommands() {
        CommandMap commandMap = Bukkit.getCommandMap();

        String cmdName = configManager.getCommandName();
        commandMap.register(cmdName, "imperium",
            new ImperiumCommand(this, configManager, dataManager));

        String chatCmd = configManager.getChatCommandName();
        commandMap.register(chatCmd, "imperium",
            new IChatCommand(this, configManager, dataManager));

        getLogger().info("Командалар тіркелді: /" + cmdName + ", /" + chatCmd);
    }

    // ── Геттерлер ──
    public static Main getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DataManager getDataManager() { return dataManager; }
    public Economy getEconomy() { return economy; }
    public boolean hasEconomy() { return economy != null; }
}
