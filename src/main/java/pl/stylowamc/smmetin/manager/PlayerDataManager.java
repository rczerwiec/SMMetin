package pl.stylowamc.smmetin.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.stylowamc.smmetin.Smmetin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerDataManager {
    private final Smmetin plugin;
    private final File dataFolder;
    private final Map<UUID, YamlConfiguration> playerDataCache;
    private final boolean debug;

    public PlayerDataManager(Smmetin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.playerDataCache = new ConcurrentHashMap<>();
        this.debug = plugin.getConfig().getBoolean("settings.debug", true);
        
        // Tworzenie katalogu, jeśli nie istnieje
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        if (debug) {
            debugLog("Inicjalizacja menedżera danych graczy");
            debugLog("Katalog danych: " + dataFolder.getAbsolutePath());
        }
    }
    
    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [PlayerData] " + message);
        }
    }
    
    private File getPlayerFile(UUID playerUUID) {
        return new File(dataFolder, playerUUID.toString() + ".yml");
    }
    
    private YamlConfiguration getPlayerConfig(UUID playerUUID) {
        // Jeśli dane są w pamięci podręcznej, zwróć je
        if (playerDataCache.containsKey(playerUUID)) {
            return playerDataCache.get(playerUUID);
        }
        
        // W przeciwnym razie wczytaj dane z pliku
        File playerFile = getPlayerFile(playerUUID);
        YamlConfiguration config;
        
        if (playerFile.exists()) {
            config = YamlConfiguration.loadConfiguration(playerFile);
            if (debug) {
                debugLog("Wczytano dane gracza: " + playerUUID);
            }
        } else {
            config = new YamlConfiguration();
            config.set("lastKnownName", Bukkit.getOfflinePlayer(playerUUID).getName());
            config.set("firstJoin", System.currentTimeMillis());
            
            // Inicjalizacja sekcji statystyk
            config.createSection("stats.metins.destroyed");
            
            if (debug) {
                debugLog("Utworzono nowe dane dla gracza: " + playerUUID);
            }
        }
        
        // Dodaj do pamięci podręcznej
        playerDataCache.put(playerUUID, config);
        return config;
    }
    
    public void savePlayerData(UUID playerUUID) {
        if (!playerDataCache.containsKey(playerUUID)) {
            if (debug) {
                debugLog("Brak danych w pamięci podręcznej dla gracza: " + playerUUID);
            }
            return;
        }
        
        YamlConfiguration config = playerDataCache.get(playerUUID);
        File playerFile = getPlayerFile(playerUUID);
        
        try {
            config.save(playerFile);
            if (debug) {
                debugLog("Zapisano dane gracza: " + playerUUID);
            }
        } catch (IOException e) {
            System.err.println("[SMMetin] Błąd podczas zapisywania danych gracza: " + playerUUID);
            e.printStackTrace();
        }
    }
    
    public void saveAllPlayerData() {
        if (debug) {
            debugLog("Zapisywanie danych wszystkich graczy...");
        }
        
        for (UUID playerUUID : playerDataCache.keySet()) {
            savePlayerData(playerUUID);
        }
        
        if (debug) {
            debugLog("Zapisano dane dla " + playerDataCache.size() + " graczy");
        }
    }
    
    public void incrementMetinDestroyed(Player player, String metinType) {
        UUID playerUUID = player.getUniqueId();
        YamlConfiguration config = getPlayerConfig(playerUUID);
        
        // Aktualizuj liczbę zniszczonych Metinów danego typu
        String path = "stats.metins.destroyed." + metinType;
        int destroyed = config.getInt(path, 0) + 1;
        config.set(path, destroyed);
        
        // Aktualizuj łączną liczbę zniszczonych Metinów
        String totalPath = "stats.metins.destroyed.total";
        int total = config.getInt(totalPath, 0) + 1;
        config.set(totalPath, total);
        
        // Aktualizuj ostatnią nazwę gracza
        config.set("lastKnownName", player.getName());
        
        if (debug) {
            debugLog("Zaktualizowano statystyki dla gracza " + player.getName() + ": " + 
                    metinType + " = " + destroyed + ", total = " + total);
        }
        
        // Zapisz dane
        savePlayerData(playerUUID);
    }
    
    public Map<String, Integer> getPlayerMetinStats(UUID playerUUID) {
        YamlConfiguration config = getPlayerConfig(playerUUID);
        Map<String, Integer> stats = new HashMap<>();
        
        // Pobierz sekcję zniszczonych Metinów
        if (config.isConfigurationSection("stats.metins.destroyed")) {
            for (String key : config.getConfigurationSection("stats.metins.destroyed").getKeys(false)) {
                if (!key.equals("total")) {
                    stats.put(key, config.getInt("stats.metins.destroyed." + key, 0));
                }
            }
        }
        
        return stats;
    }
    
    public int getTotalDestroyed(UUID playerUUID) {
        YamlConfiguration config = getPlayerConfig(playerUUID);
        return config.getInt("stats.metins.destroyed.total", 0);
    }
    
    public int getSpecificMetinDestroyed(UUID playerUUID, String metinType) {
        YamlConfiguration config = getPlayerConfig(playerUUID);
        return config.getInt("stats.metins.destroyed." + metinType, 0);
    }
    
    public List<Map.Entry<OfflinePlayer, Integer>> getTopPlayers(String metinType, int limit) {
        Map<OfflinePlayer, Integer> playerScores = new HashMap<>();
        
        // Wczytaj dane wszystkich graczy
        File[] playerFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (playerFiles != null) {
            for (File file : playerFiles) {
                String uuidString = file.getName().replace(".yml", "");
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                    
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    
                    String path = "stats.metins.destroyed." + (metinType != null ? metinType : "total");
                    int count = config.getInt(path, 0);
                    
                    if (count > 0) {
                        playerScores.put(offlinePlayer, count);
                    }
                } catch (IllegalArgumentException e) {
                    // Ignoruj nieprawidłowe UUID
                }
            }
        }
        
        // Posortuj graczy według liczby zniszczonych Metinów (malejąco)
        return playerScores.entrySet()
                .stream()
                .sorted(Map.Entry.<OfflinePlayer, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public void cleanup() {
        saveAllPlayerData();
        playerDataCache.clear();
    }
} 