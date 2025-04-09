package pl.stylowamc.smmetin;

import org.bukkit.plugin.java.JavaPlugin;
import pl.stylowamc.smmetin.commands.MetinCommand;
import pl.stylowamc.smmetin.commands.RewardsCommand;
import pl.stylowamc.smmetin.listeners.MetinListener;
import pl.stylowamc.smmetin.manager.EconomyManager;
import pl.stylowamc.smmetin.manager.MetinManager;
import pl.stylowamc.smmetin.manager.PlayerDataManager;
import pl.stylowamc.smmetin.placeholders.SMMetinExpansion;

public final class Smmetin extends JavaPlugin {

    private static Smmetin instance;
    private MetinManager metinManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        instance = this;
        
        // Zapisujemy domyślną konfigurację
        saveDefaultConfig();
        saveResource("metins.yml", false);

        // Inicjalizacja menedżerów
        this.economyManager = new EconomyManager(this);
        this.metinManager = new MetinManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        
        // Rejestracja listenerów
        getServer().getPluginManager().registerEvents(
            new MetinListener(this), 
            this
        );
        
        // Rejestracja komend
        getCommand("metin").setExecutor(new MetinCommand(this));
        getCommand("metinrewards").setExecutor(new RewardsCommand(this));
        
        // Rejestracja placeholderów
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SMMetinExpansion(this).register();
            getLogger().info("Zarejestrowano placeholdery SMMetin!");
        } else {
            getLogger().warning("Nie znaleziono PlaceholderAPI! Placeholdery nie będą działać.");
        }
        
        getLogger().info("Plugin SMMetin został uruchomiony!");
    }

    @Override
    public void onDisable() {
        if (metinManager != null) {
            metinManager.cleanup();
        }
        
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayerData();
        }
        
        getLogger().info("Plugin SMMetin został wyłączony!");
    }

    public static Smmetin getInstance() {
        return instance;
    }

    public MetinManager getMetinManager() {
        return metinManager;
    }
    
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
