package pl.stylowamc.smmetin.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pl.stylowamc.smmetin.Smmetin;
import pl.stylowamc.smmetin.metin.Metin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;
import org.bukkit.scheduler.BukkitTask;

public class MetinManager {
    private final Smmetin plugin;
    private final Map<String, Metin> activeMetins;
    private YamlConfiguration metinsConfig;
    private final int spawnRadius;
    private final int minRespawnMinutes;
    private final int maxRespawnMinutes;
    private final int maxSpawnAttempts = 10; // Maksymalna liczba prób respawnu
    private final AtomicInteger metinCounter;
    private boolean debug;
    private long nextSpawnTime = 0;
    private BukkitTask spawnTask;

    public MetinManager(Smmetin plugin) {
        this.plugin = plugin;
        this.activeMetins = new HashMap<>();
        
        // Wczytaj konfigurację
        File metinsFile = new File(plugin.getDataFolder(), "metins.yml");
        this.metinsConfig = YamlConfiguration.loadConfiguration(metinsFile);
        
        // Wczytaj ustawienia
        ConfigurationSection settings = metinsConfig.getConfigurationSection("settings");
        this.spawnRadius = settings.getInt("spawn-radius", 1000);
        this.minRespawnMinutes = settings.getInt("respawn-time.min", 10);
        this.maxRespawnMinutes = settings.getInt("respawn-time.max", 30);
        this.debug = settings.getBoolean("debug", true);
        
        this.metinCounter = new AtomicInteger(0);
        
        if (debug) {
            debugLog("Ustawienia menedżera Metinów:");
            debugLog("- Promień spawnu: " + spawnRadius);
            debugLog("- Minimalny czas respawnu: " + minRespawnMinutes + " minut");
            debugLog("- Maksymalny czas respawnu: " + maxRespawnMinutes + " minut");
        }
        
        // Rozpocznij timer respawnu
        startSpawnTimer();
    }

    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [Manager] " + message);
        }
    }

    private void startSpawnTimer() {
        // Ustawienie początkowego spawnu tuż po starcie serwera (5 sekund)
        long initialDelay = 20L * 5L; // 5 sekund po starcie serwera
        long checkPeriod = 20L * 10L; // Sprawdzaj co 10 sekund
        
        // Ustaw pierwszy czas respawnu
        setNextSpawnTime();
        
        if (debug) {
            debugLog("Timer respawnu uruchomiony. Następny spawn za " + 
                    getTimeUntilNextSpawn() + " minut.");
            debugLog("Czas respawnu ustawiony na " + new Date(nextSpawnTime));
        }
        
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Sprawdź, czy nadszedł czas na respawn
            if (System.currentTimeMillis() >= nextSpawnTime) {
                if (debug) {
                    debugLog("Nadszedł czas na respawn Metina! " + new Date());
                }
                
                // Spawnuj Metina
                spawnRandomMetin();
                
                // Ustaw nowy czas respawnu
                setNextSpawnTime();
                
                if (debug) {
                    debugLog("Następny spawn za " + getTimeUntilNextSpawn() + " minut");
                    debugLog("Dokładny czas następnego spawnu: " + new Date(nextSpawnTime));
                }
            } else if (debug && ThreadLocalRandom.current().nextInt(30) == 0) { // Czasami loguj pozostały czas
                debugLog("Pozostało " + getTimeUntilNextSpawn() + " minut do następnego spawnu.");
            }
        }, initialDelay, checkPeriod);
        
        // Dodatkowy timer do respawnienia metinów po starcie serwera
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (debug) {
                debugLog("Wymuszenie spawnu początkowego Metina");
            }
            spawnRandomMetin();
        }, initialDelay);
    }
    
    private void setNextSpawnTime() {
        // Sprawdź czy minRespawnMinutes nie jest większe lub równe maxRespawnMinutes
        if (minRespawnMinutes >= maxRespawnMinutes) {
            if (debug) {
                debugLog("BŁĄD: minRespawnMinutes (" + minRespawnMinutes + 
                         ") jest większe lub równe maxRespawnMinutes (" + maxRespawnMinutes + 
                         "). Używam wartości domyślnych.");
            }
            
            // Użyj bezpiecznych wartości domyślnych
            int respawnMinutes = 10;
            long respawnMillis = respawnMinutes * 60L * 1000L;
            nextSpawnTime = System.currentTimeMillis() + respawnMillis;
            
            if (debug) {
                debugLog("Ustawiono następny respawn za " + respawnMinutes + " minut (wartość awaryjną)");
                debugLog("Timestamp następnego respawnu: " + nextSpawnTime);
            }
            return;
        }
        
        // Losuj czas między min a max (w minutach)
        int respawnMinutes = ThreadLocalRandom.current().nextInt(
                minRespawnMinutes, maxRespawnMinutes + 1);
        
        // Przelicz na milisekundy
        long respawnMillis = respawnMinutes * 60L * 1000L;
        
        // Ustaw następny czas respawnu
        nextSpawnTime = System.currentTimeMillis() + respawnMillis;
        
        if (debug) {
            debugLog("Ustawiono następny respawn za " + respawnMinutes + " minut");
            debugLog("Timestamp następnego respawnu: " + nextSpawnTime);
        }
    }
    
    private long getTimeUntilNextSpawn() {
        long timeLeft = nextSpawnTime - System.currentTimeMillis();
        if (timeLeft <= 0) return 0;
        
        // Konwersja z milisekund na minuty
        return timeLeft / (60L * 1000L);
    }

    public void forceSpawnRandomMetin() {
        spawnRandomMetin();
    }

    public void forceSpawnMetin(String metinType) {
        forceSpawnMetin(metinType, null);
    }

    public void forceSpawnMetin(String type, Location location) {
        ConfigurationSection metinsSection = metinsConfig.getConfigurationSection("metins");
        if (metinsSection == null) {
            if (debug) debugLog("Brak sekcji 'metins' w konfiguracji");
            return;
        }

        ConfigurationSection metinConfig = metinsSection.getConfigurationSection(type);
        if (metinConfig == null) {
            if (debug) debugLog("Nie znaleziono konfiguracji dla typu: " + type);
            return;
        }

        // Jeśli lokalizacja została podana, sprawdź czy biom jest odpowiedni
        if (location != null) {
            List<String> allowedBiomes = metinConfig.getStringList("biomes");
            if (!allowedBiomes.isEmpty()) {
                String currentBiome = location.getBlock().getBiome().name();
                if (!allowedBiomes.contains(currentBiome)) {
                    if (debug) {
                        debugLog("Podana lokalizacja ma biom " + currentBiome + 
                                ", który nie jest dozwolony dla typu " + type);
                        debugLog("Dozwolone biomy: " + allowedBiomes);
                    }
                    return;
                }
            }
        } else {
            // Jeśli lokalizacja nie jest określona, znajdź losową lokalizację
            location = findRandomLocation(type);
            if (location == null) {
                if (debug) debugLog("Nie można znaleźć odpowiedniej lokalizacji dla typu: " + type);
                return;
            }
        }

        // Sprawdź, czy lokalizacja nie jest w wodzie
        boolean isInWater = isLocationInWater(location);
        if (isInWater) {
            if (debug) debugLog("Wykryto spawn na wodzie, próba ponownego spawnu...");
            
            // Szukaj pobliskiego bloku nie będącego wodą
            Location newLocation = findNearbyNonWaterLocation(location);
            if (newLocation != null) {
                location = newLocation;
                
                // Sprawdź czy nowa lokalizacja ma odpowiedni biom
                List<String> allowedBiomes = metinConfig.getStringList("biomes");
                if (!allowedBiomes.isEmpty()) {
                    String currentBiome = location.getBlock().getBiome().name();
                    if (!allowedBiomes.contains(currentBiome)) {
                        if (debug) {
                            debugLog("Nowa lokalizacja ma biom " + currentBiome + 
                                    ", który nie jest dozwolony dla typu " + type);
                        }
                        return;
                    }
                }
                
                if (debug) {
                    debugLog("Znaleziono pobliską lokalizację bez wody: " + 
                            location.getX() + ", " + location.getY() + ", " + location.getZ());
                }
            } else {
                if (debug) debugLog("Nie udało się znaleźć lokalizacji poza wodą");
                return;
            }
        }

        // Generuj unikalny identyfikator
        String id = type + "_" + getNextMetinId();
        
        String displayName = ChatColor.translateAlternateColorCodes('&', 
            metinConfig.getString("display-name", "&7Metin"));
        double health = metinConfig.getDouble("health", 50.0);
        
        // Tworzenie nowego Metina
        try {
            Metin metin = new Metin(id, displayName, location, health, metinConfig, plugin);
            activeMetins.put(id, metin);
            
            if (debug) debugLog("Stworzono Metina: " + id + " na koordynatach: " + 
                              location.getX() + ", " + location.getY() + ", " + location.getZ() + 
                              " (biom: " + location.getBlock().getBiome().name() + ")");
            
            // Powiadomiamy użytkowników o nowym Metinie, ale bez wyświetlania jego typu
            String message = metinsConfig.getString("messages.metin-spawn", 
                    "&aMetin pojawił się na koordynatach: &e{x}, {y}, {z}")
                    .replace("{x}", String.valueOf((int) location.getX()))
                    .replace("{y}", String.valueOf((int) location.getY()))
                    .replace("{z}", String.valueOf((int) location.getZ()));
            
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            
            saveMetinData();
        } catch (Exception e) {
            if (debug) {
                debugLog("Błąd podczas tworzenia Metina: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Location findRandomLocation(String metinType) {
        if (debug) {
            debugLog("Szukam odpowiedniej lokalizacji dla Metina typu: " + metinType);
        }
        
        ConfigurationSection metinConfig = metinsConfig.getConfigurationSection("metins." + metinType);
        if (metinConfig == null) {
            if (debug) {
                debugLog("Nie znaleziono konfiguracji dla typu: " + metinType);
            }
            return null;
        }
        
        // Pobierz listę dozwolonych biomów
        List<String> allowedBiomes = metinConfig.getStringList("biomes");
        if (allowedBiomes.isEmpty()) {
            if (debug) {
                debugLog("Brak zdefiniowanych biomów dla typu: " + metinType + ", używam wszystkich biomów");
            }
            return getRandomLocation();
        }
        
        if (debug) {
            debugLog("Dozwolone biomy dla typu " + metinType + ": " + allowedBiomes);
        }
        
        // Pobierz świat
        World world = Bukkit.getWorlds().get(0);
        
        // Wykonaj próby znalezienia odpowiedniego biomu
        for (int attempt = 0; attempt < maxSpawnAttempts * 3; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
            int z = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
            int y = world.getHighestBlockYAt(x, z);
            
            Location location = new Location(world, x, y, z);
            
            // Sprawdź czy lokalizacja jest w wodzie
            if (isLocationInWater(location)) {
                if (debug && attempt % 10 == 0) {
                    debugLog("Próba " + (attempt + 1) + " - lokalizacja w wodzie, szukam dalej...");
                }
                continue;
            }
            
            // Sprawdź czy biom pasuje do dozwolonych
            String biomeName = location.getBlock().getBiome().name();
            if (allowedBiomes.contains(biomeName)) {
                if (debug) {
                    debugLog("Znaleziono odpowiednią lokalizację na biomie " + biomeName + 
                            " dla typu " + metinType + " na koordynatach: " + 
                            location.getX() + ", " + location.getY() + ", " + location.getZ());
                }
                return location;
            }
            
            if (debug && attempt % 10 == 0) {
                debugLog("Próba " + (attempt + 1) + " - biom " + biomeName + 
                        " nie jest dozwolony dla typu " + metinType + ", szukam dalej...");
            }
        }
        
        // Jeśli nie udało się znaleźć odpowiedniego biomu
        if (debug) {
            debugLog("OSTRZEŻENIE: Nie udało się znaleźć odpowiedniego biomu dla typu: " + 
                    metinType + " po " + (maxSpawnAttempts * 3) + " próbach.");
            debugLog("Dozwolone biomy: " + allowedBiomes);
        }
        
        // Wyświetl ostrzeżenie w konsoli
        Bukkit.getLogger().warning("[SMMetin] Nie udało się znaleźć odpowiedniego biomu dla typu: " + 
                metinType + ". Dostępne biomy: " + allowedBiomes);
        
        return null;
    }

    private void spawnRandomMetin() {
        ConfigurationSection metinsSection = metinsConfig.getConfigurationSection("metins");
        if (metinsSection == null) return;

        List<String> availableMetins = new ArrayList<>(metinsSection.getKeys(false));
        if (availableMetins.isEmpty()) return;

        // Sprawdź, czy używamy domyślnych szans (równych) czy niestandardowych
        ConfigurationSection spawnChancesSection = metinsConfig.getConfigurationSection("settings.spawn-chances");
        boolean useDefaultChances = spawnChancesSection == null || spawnChancesSection.getBoolean("default", true);
        
        Map<String, Double> spawnChances = new HashMap<>();
        
        if (useDefaultChances) {
            // Oblicz szansę na spawn każdego typu Metina (równomierne rozłożenie 100%)
            double chancePerMetin = 1.0 / availableMetins.size();
            for (String metinType : availableMetins) {
                spawnChances.put(metinType, chancePerMetin);
            }
            
            if (debug) {
                debugLog("Używam domyślnych szans: " + (chancePerMetin * 100) + "% dla każdego typu");
            }
        } else {
            // Pobierz niestandardowe szanse z konfiguracji
            double totalChance = 0.0;
            for (String metinType : availableMetins) {
                double chance = spawnChancesSection.getDouble(metinType, 0.0);
                spawnChances.put(metinType, chance);
                totalChance += chance;
            }
            
            // Sprawdź czy suma szans jest bliska 1.0 (100%)
            if (Math.abs(totalChance - 1.0) > 0.05) { // Dopuszczamy błąd 5%
                if (debug) {
                    debugLog("UWAGA: Suma szans (" + totalChance + ") różni się znacząco od 1.0. Normalizuję szanse.");
                }
                
                // Normalizuj szanse, aby sumowały się do 1.0
                for (String metinType : spawnChances.keySet()) {
                    spawnChances.put(metinType, spawnChances.get(metinType) / totalChance);
                }
            }
            
            if (debug) {
                debugLog("Używam niestandardowych szans z konfiguracji: " + spawnChances);
            }
        }

        // Losuj liczbę od 0 do 1
        double rand = ThreadLocalRandom.current().nextDouble();
        if (debug) {
            debugLog("Wylosowana wartość: " + rand);
        }

        // Wybierz typ Metina na podstawie wylosowanej wartości i skumulowanych szans
        String selectedMetinType = null;
        double cumulativeChance = 0.0;

        for (String metinType : availableMetins) {
            cumulativeChance += spawnChances.get(metinType);
            if (rand <= cumulativeChance) {
                selectedMetinType = metinType;
                break;
            }
        }

        // Jeśli z jakiegoś powodu nie wybrano typu, wybierz ostatni
        if (selectedMetinType == null) {
            selectedMetinType = availableMetins.get(availableMetins.size() - 1);
            if (debug) {
                debugLog("Nie wybrano typu metina na podstawie szans, używam ostatniego typu: " + selectedMetinType);
            }
        }

        if (debug) {
            debugLog("Wybrany typ Metina do spawnu: " + selectedMetinType + 
                    " (szansa: " + (spawnChances.get(selectedMetinType) * 100) + "%)");
        }

        // Próbuj znaleźć odpowiednią lokalizację dla wybranego typu
        Location location = findRandomLocation(selectedMetinType);
        if (location != null) {
            forceSpawnMetin(selectedMetinType, location);
            return;
        }

        // Jeśli nie udało się znaleźć lokalizacji dla wybranego typu, próbuj po kolei z pozostałymi
        if (debug) {
            debugLog("Nie udało się znaleźć odpowiedniej lokalizacji dla typu: " + selectedMetinType + 
                    ", próbuję z pozostałymi typami...");
        }

        // Utwórz listę pozostałych typów posortowaną według szansy (od największej do najmniejszej)
        List<String> remainingTypes = new ArrayList<>(availableMetins);
        remainingTypes.remove(selectedMetinType);
        remainingTypes.sort((t1, t2) -> Double.compare(spawnChances.get(t2), spawnChances.get(t1)));
        
        for (String metinType : remainingTypes) {
            location = findRandomLocation(metinType);
            if (location != null) {
                forceSpawnMetin(metinType, location);
                if (debug) {
                    debugLog("Udało się znaleźć lokalizację dla alternatywnego typu: " + metinType);
                }
                return;
            }
            
            if (debug) {
                debugLog("Nie udało się znaleźć odpowiedniej lokalizacji dla typu: " + metinType + 
                        ", próbuję z kolejnym typem...");
            }
        }
        
        // Jeśli nie udało się dla żadnego typu
        if (debug) {
            debugLog("OSTRZEŻENIE: Nie udało się znaleźć odpowiedniej lokalizacji dla żadnego typu Metina!");
        }
        
        // Wyświetl ostrzeżenie w konsoli
        Bukkit.getLogger().warning("[SMMetin] Nie udało się znaleźć odpowiedniej lokalizacji dla żadnego typu Metina! " +
                "Sprawdź konfigurację biomów lub zwiększ promień spawnu.");
    }

    private Location getRandomLocation() {
        World world = Bukkit.getWorlds().get(0); // Używamy głównego świata
        
        // Wykonaj kilka prób znalezienia lokalizacji
        for (int attempt = 0; attempt < maxSpawnAttempts; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
            int z = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
            int y = world.getHighestBlockYAt(x, z);
            
            Location location = new Location(world, x, y, z);
            
            // Sprawdź, czy lokalizacja nie jest w wodzie
            if (!isLocationInWater(location)) {
                if (debug) {
                    debugLog("Znaleziono odpowiednią lokalizację na koordynatach: " + 
                            location.getX() + ", " + location.getY() + ", " + location.getZ());
                }
                return location;
            }
            
            if (debug) {
                debugLog("Próba " + (attempt + 1) + " - lokalizacja w wodzie, szukam dalej...");
            }
        }
        
        // Jeśli nie udało się znaleźć lokalizacji poza wodą, zwróć ostatnią próbę
        int x = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
        int z = ThreadLocalRandom.current().nextInt(-spawnRadius, spawnRadius + 1);
        int y = world.getHighestBlockYAt(x, z);
        
        Location location = new Location(world, x, y, z);
        if (debug) {
            debugLog("Nie udało się znaleźć lokalizacji poza wodą po " + maxSpawnAttempts + 
                    " próbach. Używam ostatniej lokalizacji.");
        }
        
        return location;
    }
    
    /**
     * Sprawdza, czy lokalizacja jest w wodzie
     */
    private boolean isLocationInWater(Location location) {
        Block block = location.getBlock();
        Block blockBelow = location.clone().subtract(0, 1, 0).getBlock();
        
        return block.getType() == Material.WATER || 
               blockBelow.getType() == Material.WATER ||
               block.getType() == Material.LAVA || 
               blockBelow.getType() == Material.LAVA;
    }
    
    /**
     * Znajduje pobliską lokalizację, która nie jest w wodzie
     */
    private Location findNearbyNonWaterLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return null;
        
        // Sprawdź w promieniu 10 bloków
        for (int r = 1; r <= 10; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    // Sprawdzaj tylko bloki na obwodzie kwadratu
                    if (Math.abs(x) != r && Math.abs(z) != r) continue;
                    
                    Location newLoc = location.clone().add(x, 0, z);
                    // Znajdź najwyższy stały blok w tej lokalizacji
                    newLoc.setY(world.getHighestBlockYAt(newLoc));
                    
                    if (!isLocationInWater(newLoc)) {
                        return newLoc;
                    }
                }
            }
        }
        
        return null;
    }

    public void cleanup() {
        if (debug) {
            debugLog("Rozpoczynam czyszczenie wszystkich Metinów...");
            debugLog("Liczba aktywnych Metinów przed czyszczeniem: " + activeMetins.size());
        }

        for (Metin metin : new ArrayList<>(activeMetins.values())) {
            metin.cleanup();
        }
        activeMetins.clear();

        if (debug) {
            debugLog("Zakończono czyszczenie wszystkich Metinów");
        }
    }

    public Metin getMetin(String metinId) {
        return activeMetins.get(metinId);
    }

    public Metin getMetinAtLocation(Location location) {
        return activeMetins.values().stream()
            .filter(metin -> metin.isMetinBlock(location.getBlock()))
            .findFirst()
            .orElse(null);
    }

    public void removeMetin(String metinId) {
        if (debug) {
            debugLog("Usuwanie Metina: " + metinId);
        }

        Metin metin = activeMetins.remove(metinId);
        if (metin != null) {
            if (debug) {
                debugLog("Usunięto Metina: " + metinId);
                debugLog("Pozostałe aktywne Metiny: " + activeMetins.keySet());
            }
        } else if (debug) {
            debugLog("Nie znaleziono Metina o ID: " + metinId);
        }
    }

    public void removeAllMetins() {
        if (debug) {
            debugLog("Usuwanie wszystkich Metinów...");
            debugLog("Liczba Metinów do usunięcia: " + activeMetins.size());
        }

        // Tworzymy kopię kolekcji Metinów
        Collection<Metin> metinsToRemove = new ArrayList<>(activeMetins.values());
        
        // Usuwamy wszystkie Metiny
        for (Metin metin : metinsToRemove) {
            if (debug) {
                debugLog("Usuwanie Metina: " + metin.getId());
            }
            metin.cleanup();
        }
        
        // Czyścimy mapę
        activeMetins.clear();

        if (debug) {
            debugLog("Zakończono usuwanie wszystkich Metinów");
        }

        // Wysyłamy wiadomość o usunięciu wszystkich Metinów
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', 
            metinsConfig.getString("messages.all-metins-removed", "&aWszystkie Metiny zostały usunięte!")));
    }

    public boolean teleportPlayerToMetin(Player player, String metinId) {
        Metin metin = activeMetins.get(metinId);
        if (metin != null) {
            Location safeLoc = metin.getLocation().clone().add(0, 1, 0);
            player.teleport(safeLoc);
            
            String message = metinsConfig.getString("messages.teleported-to-metin", "&aPrzeteleportowano do Metina &e{name}");
            message = ChatColor.translateAlternateColorCodes('&', message
                .replace("{name}", metin.getDisplayName()));
            player.sendMessage(message);
            
            if (debug) {
                debugLog("Przeteleportowano gracza " + player.getName() + " do Metina " + metinId);
            }
            return true;
        }
        
        String message = metinsConfig.getString("messages.metin-not-found", "&cNie znaleziono Metina o ID: &e{id}");
        message = ChatColor.translateAlternateColorCodes('&', message.replace("{id}", metinId));
        player.sendMessage(message);
        return false;
    }

    // Tylko niezbędny getter
    public YamlConfiguration getMetinsConfig() {
        return metinsConfig;
    }

    private Location findSuitableLocation() {
        // Implementacja logiki znajdowania odpowiedniej lokalizacji
        return getRandomLocation();
    }

    public Collection<Metin> getActiveMetins() {
        return new ArrayList<>(activeMetins.values());
    }

    private int getNextMetinId() {
        return metinCounter.incrementAndGet();
    }

    private void saveMetinData() {
        // Na razie pusta implementacja - tu można dodać zapis danych do pliku jeśli będzie potrzebny
        if (debug) {
            debugLog("Zapisywanie danych Metinów...");
            debugLog("Aktywne Metiny: " + activeMetins.size());
        }
    }

    public void forceRefreshNextSpawnTime() {
        setNextSpawnTime();
        if (debug) {
            debugLog("Wymuszono odświeżenie czasu respawnu. Następny spawn za " + 
                    getTimeUntilNextSpawn() + " minut");
        }
    }

    /**
     * Przeładowuje konfigurację metinów z pliku
     */
    public void reloadConfig() {
        if (debug) {
            debugLog("Przeładowywanie konfiguracji metinów...");
        }
        
        // Przeładuj plik konfiguracji
        File metinsFile = new File(plugin.getDataFolder(), "metins.yml");
        YamlConfiguration newConfig = YamlConfiguration.loadConfiguration(metinsFile);
        
        // Zaktualizuj główną referencję do konfiguracji
        this.metinsConfig = newConfig;
        
        // Zaktualizuj referencję do konfiguracji w aktywnych metinach
        for (Metin metin : activeMetins.values()) {
            String metinType = metin.getId().split("_")[0]; // Przyjmujemy format ID: typ_numer
            ConfigurationSection metinConfig = newConfig.getConfigurationSection("metins." + metinType);
            if (metinConfig != null) {
                metin.updateConfig(metinConfig);
            }
        }
        
        if (debug) {
            debugLog("Konfiguracja metinów została przeładowana");
        }
    }

    /**
     * Sprawdza, czy debug jest włączony
     * @return true jeśli debug jest włączony, false w przeciwnym razie
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Ustawia status debugowania
     * @param debug nowy status debugowania
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    /**
     * Zwraca plik konfiguracyjny metinów
     * @return plik konfiguracyjny metinów
     */
    public File getMetinsFile() {
        return new File(plugin.getDataFolder(), "metins.yml");
    }
} 