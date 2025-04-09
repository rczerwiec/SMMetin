package pl.stylowamc.smmetin.metin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Stray;
import org.bukkit.plugin.Plugin;
import org.bukkit.*;
import pl.stylowamc.smmetin.Smmetin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

public class Metin {
    private final String id;
    private final String displayName;
    private final Location location;
    private double maxHealth;
    private double currentHealth;
    private final Map<UUID, Double> damageContributors;
    private final List<ArmorStand> holograms;
    private final List<LivingEntity> spawnedMobs;
    private Block metinBlock;
    private Block metinBlockTop;
    private Material originalBlockType;
    private Material originalBlockTopType;
    private final Random random;
    private ConfigurationSection config;
    private final boolean debug;
    private int mobSpawnTaskId = -1;
    private BukkitTask particleTask;
    private BukkitTask lifetimeTask;
    private long lastKnockbackTime = 0;
    private Plugin plugin;
    private long spawnTime;
    private final Map<Location, Material> lavaBlocks = new HashMap<>();

    public Metin(String id, String displayName, Location location, double health, ConfigurationSection config, Plugin plugin) {
        this.id = id;
        this.displayName = displayName;
        this.location = findValidLocation(location, 10);
        if (this.location == null) {
            throw new IllegalStateException("Nie można znaleźć odpowiedniej lokalizacji dla Metina!");
        }
        this.maxHealth = health;
        this.currentHealth = health;
        this.damageContributors = new HashMap<>();
        this.holograms = new ArrayList<>();
        this.spawnedMobs = new ArrayList<>();
        this.random = new Random();
        this.config = config;
        this.debug = config.getRoot().getConfigurationSection("settings").getBoolean("debug", true);
        this.plugin = plugin;
        this.spawnTime = System.currentTimeMillis();
        
        createMetinBlock();
        createHologram();
        startLifetimeTimer();
        if (debug) {
            debugLog("Utworzono nowego Metina: " + id + " (" + displayName + ") z " + health + " HP");
        }
    }

    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [Metin:" + id + "] " + message);
        }
    }

    private boolean canCreateMetin(Block block) {
        Block above = block.getRelative(BlockFace.UP);
        Block below = block.getRelative(BlockFace.DOWN);
        
        // Sprawdź czy blok pod Metinem jest solidny
        boolean solidGround = below.getType().isSolid() && !below.getType().name().contains("LEAVES");
        
        // Sprawdzamy czy blok lub blok nad nim to woda
        boolean notWater = !block.getType().equals(Material.WATER) && 
               !above.getType().equals(Material.WATER) &&
               !block.isLiquid() && 
               !above.isLiquid();
               
        // Sprawdzamy czy blok lub blok nad nim to liście
        boolean notLeaves = !block.getType().name().contains("LEAVES") &&
                !above.getType().name().contains("LEAVES");
                
        // Sprawdzamy czy blok lub blok nad nim to barrier
        boolean notBarrier = !block.getType().equals(Material.BARRIER) &&
                !above.getType().equals(Material.BARRIER);
                
        // Sprawdź czy lokacja nie jest w chronionym regionie
        boolean notInRegion = true;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager regions = container.get(BukkitAdapter.adapt(block.getWorld()));
                
                if (regions != null) {
                    BlockVector3 vec = BlockVector3.at(
                        block.getX(), 
                        block.getY(), 
                        block.getZ()
                    );
                    
                    notInRegion = regions.getApplicableRegions(vec).size() == 0;
                    
                    if (debug && !notInRegion) {
                        debugLog("Nie można utworzyć Metina - lokacja znajduje się w chronionym regionie!");
                    }
                }
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd podczas sprawdzania regionów: " + e.getMessage());
                }
            }
        }
                
        if (debug) {
            if (!solidGround) {
                debugLog("Nie można utworzyć Metina - brak solidnego podłoża!");
            }
            if (!notLeaves) {
                debugLog("Nie można utworzyć Metina na liściach drzewa!");
            }
            if (!notBarrier) {
                debugLog("Nie można utworzyć Metina na bloku barrier!");
            }
        }
        
        return solidGround && notWater && notLeaves && notBarrier && notInRegion;
    }

    private Location findValidLocation(Location baseLocation, int maxAttempts) {
        if (maxAttempts <= 0) {
            if (debug) {
                debugLog("Osiągnięto maksymalną liczbę prób znalezienia lokalizacji");
            }
            return null;
        }
        
        // Znajdź najwyższy blok w tej lokalizacji
        Location highestBlock = baseLocation.getWorld().getHighestBlockAt(baseLocation).getLocation();
        Block block = highestBlock.getBlock();
        
        if (canCreateMetin(block)) {
            return highestBlock;
        }

        // Szukaj w coraz większym promieniu
        int radius = 5 * ((11 - maxAttempts) + 1); // Zwiększaj promień z każdą próbą
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location newLoc = baseLocation.clone().add(x, 0, z);
                // Znajdź najwyższy blok w nowej lokalizacji
                Location newHighestBlock = newLoc.getWorld().getHighestBlockAt(newLoc).getLocation();
                Block newBlock = newHighestBlock.getBlock();
                
                if (canCreateMetin(newBlock)) {
                    if (debug) {
                        debugLog("Znaleziono odpowiednią lokalizację w promieniu " + radius + " bloków");
                    }
                    return newHighestBlock;
                }
            }
        }

        // Jeśli nie znaleziono, spróbuj ponownie z większym promieniem
        return findValidLocation(baseLocation, maxAttempts - 1);
    }

    private void createMetinBlock() {
        Block block = location.getBlock().getRelative(BlockFace.UP);
        Block blockAbove = block.getRelative(BlockFace.UP);
        
        // Zapisz oryginalne bloki
        originalBlockType = block.getType();
        originalBlockTopType = blockAbove.getType();
        
        // Pobierz materiał z konfiguracji lub użyj domyślnego
        Material blockMaterial = Material.STONE;
        if (config.contains("block-material")) {
            try {
                blockMaterial = Material.valueOf(config.getString("block-material").toUpperCase());
                if (debug) {
                    debugLog("Ustawiam materiał bloku: " + blockMaterial);
                }
            } catch (IllegalArgumentException e) {
                if (debug) {
                    debugLog("Nieprawidłowy materiał w konfiguracji, używam domyślnego: STONE");
                }
            }
        }

        // Ustaw bloki
        blockAbove.setType(blockMaterial);
        block.setType(blockMaterial);
        
        metinBlock = block;
        metinBlockTop = blockAbove;

        if (debug) {
            debugLog("Utworzono bloki Metina na lokacji: " + block.getLocation());
        }

        // Rozpocznij efekt cząsteczek
        startParticleEffect();
    }

    private void startParticleEffect() {
        if (!config.contains("particles")) {
            if (debug) {
                debugLog("Brak sekcji particles w konfiguracji");
            }
            return;
        }

        ConfigurationSection particleConfig = config.getConfigurationSection("particles");
        if (particleConfig == null) {
            if (debug) {
                debugLog("Nie można odczytać sekcji particles");
            }
            return;
        }

        String particleType = particleConfig.getString("type", "FLAME");
        int count = particleConfig.getInt("count", 10);
        double radius = particleConfig.getDouble("radius", 0.5);
        double speed = particleConfig.getDouble("speed", 0.1);
        int interval = particleConfig.getInt("interval", 10);

        if (debug) {
            debugLog("Konfiguracja cząsteczek:");
            debugLog("- Typ: " + particleType);
            debugLog("- Ilość: " + count);
            debugLog("- Promień: " + radius);
            debugLog("- Prędkość: " + speed);
            debugLog("- Interwał: " + interval);
        }

        try {
            Particle particle = Particle.valueOf(particleType.toUpperCase());
            
            particleTask = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugin("SMMetin"),
                () -> {
                    if (isDestroyed()) {
                        if (particleTask != null) {
                            particleTask.cancel();
                        }
                        return;
                    }

                    Location particleLoc = metinBlockTop.getLocation().add(0.5, 0.5, 0.5);
                    for (int i = 0; i < count; i++) {
                        double angle = (2 * Math.PI * i) / count;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        particleLoc.add(x, 0, z);
                        location.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, speed);
                        particleLoc.subtract(x, 0, z);
                    }
                },
                0L,
                interval
            );

            if (debug) {
                debugLog("Uruchomiono efekt cząsteczek typu: " + particleType);
            }
        } catch (IllegalArgumentException e) {
            if (debug) {
                debugLog("Nieprawidłowy typ cząsteczek: " + particleType);
                debugLog("Error: " + e.getMessage());
            }
        }
    }

    private void createHologram() {
        if (debug) {
            debugLog("Tworzenie hologramów...");
        }

        // Usuń stare hologramy, jeśli istnieją
        removeHolograms();

        // Pobierz offsety hologramów z konfiguracji
        ConfigurationSection settingsSection = config.getRoot().getConfigurationSection("settings");
        ConfigurationSection hologramOffsets = settingsSection.getConfigurationSection("hologram-offsets");
        
        // Ustaw domyślne wartości jeśli brak konfiguracji
        double nameOffset = 1.5;
        double healthOffset = 1.2;
        
        // Pobierz wartości z konfiguracji jeśli istnieją
        if (hologramOffsets != null) {
            nameOffset = hologramOffsets.getDouble("name", 1.5);
            healthOffset = hologramOffsets.getDouble("health", 1.2);
            if (debug) {
                debugLog("Pobrano offsety hologramów z konfiguracji: nazwa=" + nameOffset + ", HP=" + healthOffset);
            }
        } else if (debug) {
            debugLog("Brak sekcji hologram-offsets w konfiguracji, używam domyślnych wartości");
        }

        // Nazwa Metina - pozycja z konfiguracji
        Location nameLoc = metinBlockTop.getLocation().clone().add(0.5, nameOffset, 0.5);
        ArmorStand nameStand = (ArmorStand) location.getWorld().spawnEntity(nameLoc, EntityType.ARMOR_STAND);
        setupArmorStand(nameStand);
        nameStand.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        nameStand.setCustomNameVisible(true);
        holograms.add(nameStand);

        if (debug) {
            debugLog("Utworzono hologram nazwy: " + displayName + " na wysokości " + nameOffset);
        }

        // HP Metina - pozycja z konfiguracji
        Location healthLoc = metinBlockTop.getLocation().clone().add(0.5, healthOffset, 0.5);
        ArmorStand healthStand = (ArmorStand) location.getWorld().spawnEntity(healthLoc, EntityType.ARMOR_STAND);
        setupArmorStand(healthStand);
        updateHealthHologram(healthStand);
        holograms.add(healthStand);

        if (debug) {
            debugLog("Utworzono hologram HP na wysokości " + healthOffset);
            debugLog("Liczba utworzonych hologramów: " + holograms.size());
        }
    }

    private void removeHolograms() {
        if (debug) {
            debugLog("Usuwanie starych hologramów...");
            debugLog("Liczba hologramów do usunięcia: " + holograms.size());
        }

        // Pobierz maksymalny offset z konfiguracji aby ustalić zakres wyszukiwania
        ConfigurationSection hologramOffsets = config.getRoot().getConfigurationSection("settings.hologram-offsets");
        double maxOffset = 2.0; // Domyślna wartość na wypadek braku konfiguracji
        
        if (hologramOffsets != null) {
            double nameOffset = hologramOffsets.getDouble("name", 1.5);
            double healthOffset = hologramOffsets.getDouble("health", 1.2);
            maxOffset = Math.max(nameOffset, healthOffset) + 1.0; // Dodajemy 1 blok zapasu
            
            if (debug) {
                debugLog("Ustawiam maksymalny offset hologramów na: " + maxOffset);
            }
        }

        // Usuń wszystkie hologramy w świecie o tych samych koordynatach (zakres dostosowany do konfiguracji)
        location.getWorld().getNearbyEntities(location.clone().add(0.5, maxOffset, 0.5), 1, maxOffset, 1).forEach(entity -> {
            if (entity instanceof ArmorStand && ((ArmorStand) entity).isMarker()) {
                entity.remove();
                if (debug) {
                    debugLog("Usunięto stary hologram na koordynatach: " + entity.getLocation());
                }
            }
        });

        // Usuń hologramy z naszej listy
        for (ArmorStand hologram : new ArrayList<>(holograms)) {
            if (hologram != null && !hologram.isDead()) {
                hologram.remove();
                if (debug) {
                    debugLog("Usunięto hologram z listy");
                }
            }
        }
        holograms.clear();

        if (debug) {
            debugLog("Zakończono usuwanie starych hologramów");
        }
    }

    private void setupArmorStand(ArmorStand stand) {
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setVisible(false);
        stand.setCustomNameVisible(true);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
    }

    private void updateHealthHologram(ArmorStand healthStand) {
        if (healthStand == null || healthStand.isDead()) {
            if (debug) {
                debugLog("Próba aktualizacji nieistniejącego hologramu HP!");
            }
            return;
        }

        String healthText = ChatColor.translateAlternateColorCodes('&', 
            String.format("&aHP: &c%.1f&7/&c%.1f", currentHealth, maxHealth));
        healthStand.setCustomName(healthText);
        healthStand.setCustomNameVisible(true);

        if (debug) {
            debugLog("Zaktualizowano HP: " + currentHealth + "/" + maxHealth);
        }
    }

    public void updateHologram() {
        if (debug) {
            debugLog("Próba aktualizacji hologramu HP...");
            debugLog("Liczba hologramów: " + holograms.size());
        }

        if (holograms.size() >= 2) {
            ArmorStand healthStand = holograms.get(1);
            if (healthStand != null && !healthStand.isDead()) {
                updateHealthHologram(healthStand);
            } else {
                if (debug) {
                    debugLog("Hologram HP jest null lub martwy - tworzę nowy");
                }
                createHologram();
            }
        } else {
            if (debug) {
                debugLog("Brak wystarczającej liczby hologramów - tworzę nowe");
            }
            createHologram();
        }
    }

    public void damage(Player player, double damage) {
        if (debug) {
            debugLog("Gracz " + player.getName() + " zadał " + damage + " obrażeń");
            debugLog("HP przed obrażeniami: " + currentHealth);
        }

        currentHealth = Math.max(0, currentHealth - damage);
        damageContributors.merge(player.getUniqueId(), damage, Double::sum);
        
        // Aktualizuj hologram HP
        updateHologram();
        
        // Efekty dźwiękowe
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f);

        // Sprawdź odpychanie
        tryKnockback(player);

        // Sprawdź czy stworzyć lawę
        trySpawnLava(player);

        if (debug) {
            debugLog("HP po obrażeniach: " + currentHealth);
        }

        // Sprawdź czy Metin został zniszczony
        if (isDestroyed()) {
            handleDestroy();
        } else {
            // Szansa na respienie mobów przy każdym uderzeniu
            if (random.nextDouble() < 0.3) { // 30% szansa na spawn mobów
                if (debug) {
                    debugLog("Wylosowano spawn mobów (szansa 30%)");
                }
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SMMetin"), () -> {
                    spawnMobs();
                });
            }
        }
    }

    private void tryKnockback(Player player) {
        ConfigurationSection knockbackConfig = config.getConfigurationSection("knockback");
        if (knockbackConfig == null) {
            // Jeśli nie ma indywidualnych ustawień, użyj globalnych
            knockbackConfig = config.getRoot().getConfigurationSection("settings.knockback");
            if (knockbackConfig == null) return;
        }

        double chance = knockbackConfig.getDouble("chance", 0.2);
        double strength = knockbackConfig.getDouble("strength", 1.5);
        int cooldown = knockbackConfig.getInt("cooldown", 40);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastKnockbackTime < cooldown * 50) return; // Przelicz ticki na milisekundy

        if (random.nextDouble() < chance) {
            // Oblicz wektor odpychania
            Vector knockbackVector = player.getLocation().toVector().subtract(location.toVector()).normalize();
            knockbackVector.setY(0.3); // Dodaj niewielkie wybicie w górę
            knockbackVector.multiply(strength);
            
            // Zastosuj odpychanie
            player.setVelocity(knockbackVector);
            lastKnockbackTime = currentTime;

            if (debug) {
                debugLog("Zastosowano odpychanie na graczu " + player.getName());
                debugLog("- Szansa: " + chance);
                debugLog("- Siła: " + strength);
                debugLog("- Cooldown: " + cooldown + " ticków");
            }
        }
    }

    private void trySpawnLava(Player player) {
        if (!config.contains("lava") || !config.getConfigurationSection("lava").getBoolean("enabled", false)) {
            return;
        }

        double chance = config.getConfigurationSection("lava").getDouble("chance", 0.02);
        if (random.nextDouble() <= chance) {
            Location playerLoc = player.getLocation().getBlock().getLocation();
            Block targetBlock = playerLoc.getBlock();
            
            // Sprawdź czy blok nie jest już lawą i czy nie jest już śledzony
            if (!targetBlock.getType().equals(Material.LAVA) && !lavaBlocks.containsKey(playerLoc)) {
                Material originalMaterial = targetBlock.getType();
                
                // Dodaj do mapy przed zmianą typu bloku
                lavaBlocks.put(playerLoc.clone(), originalMaterial);
                
                // Zmień blok na lawę
                targetBlock.setType(Material.LAVA);
                
                if (debug) {
                    debugLog("Stworzono lawę pod graczem " + player.getName());
                    debugLog("Lokalizacja lawy: " + playerLoc);
                    debugLog("Oryginalny materiał: " + originalMaterial);
                }
                
                // Zaplanuj automatyczne usunięcie lawy po 10 sekundach
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (lavaBlocks.containsKey(playerLoc)) {
                        Block block = playerLoc.getBlock();
                        if (block.getType() == Material.LAVA) {
                            block.setType(lavaBlocks.get(playerLoc));
                            lavaBlocks.remove(playerLoc);
                            if (debug) {
                                debugLog("Automatycznie usunięto lawę po 10 sekundach");
                            }
                        }
                    }
                }, 200L); // 200 ticków = 10 sekund
            }
        }
    }

    private void handleDestroy() {
        if (debug) {
            debugLog("Rozpoczęto proces zniszczenia Metina " + id);
        }
        
        // Efekty zniszczenia
        location.getWorld().createExplosion(location, 0.0f, false, false);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        
        // Oblicz całkowite obrażenia
        double totalDamage = damageContributors.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Posortuj graczy według zadanych obrażeń
        List<Map.Entry<UUID, Double>> sortedContributors = damageContributors.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        // Wyślij wiadomość o zniszczeniu Metina
        String destroyMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.metin-destroy", 
                "&c&lMetin &e&l{name} &c&lzostał zgładzony")
                .replace("{name}", displayName);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', destroyMsg));
        
        // Wyślij nagłówek rankingu
        String headerMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.metin-destroy-header",
                "&e&lRanking obrażeń:");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', headerMsg));
        
        // Przygotuj nagrody i komunikaty dla każdego gracza
        int position = 1;
        for (Map.Entry<UUID, Double> entry : sortedContributors) {
            UUID playerId = entry.getKey();
            double damage = entry.getValue();
            double percentage = (damage / totalDamage) * 100.0;
            Player player = Bukkit.getPlayer(playerId);
            
            if (player != null && player.isOnline()) {
                // Zwiększ statystyki gracza - używamy ID Metina zamiast nazwy konfiguracji
                String metinType = id.split("_")[0]; // Pobierz typ Metina z ID (format: typ_numer)
                ((Smmetin) plugin).getPlayerDataManager().incrementMetinDestroyed(player, metinType);
                
                // Przygotuj nagrody
                List<ItemStack> items = getRewardItems(percentage);
                int experience = calculateExperience(percentage);
                double money = calculateMoney(percentage);
                
                // Przyznaj nagrody
                giveRewardsToPlayer(player, items, experience, money);
                
                // Przygotuj tekst z nagrodami
                String rewardsText = formatRewardsList(items);
                
                // Nowy format komunikatu
                String entryMsg = String.format("&7%d. &f%s &7%d%% Obrażeń &8| &6%d$ &8| &a%dXP\n&7Nagrody: &e[%s]",
                        position,
                        player.getName(),
                        Math.round(percentage),
                        Math.round(money),
                        experience,
                        rewardsText);
                
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', entryMsg));
                position++;
            }
        }
        
        // Sprawdź czy wypadło trofeum i wyświetl komunikat tylko jeśli faktycznie wypadło
        if (checkAndDropTrophy()) {
            String trophyMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.metin-trophy-drop",
                    "&e&lMetin ten wydropił trofeum!");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', trophyMsg));
        }
        
        // Usuń bloki i wyczyść
        cleanup();
        
        // Usuń Metina z listy aktywnych Metinów
        ((Smmetin) plugin).getMetinManager().removeMetin(id);
        
        if (debug) {
            debugLog("Zakończono proces zniszczenia Metina " + id);
        }
    }

    private double calculateMoney(double percentage) {
        if (config.contains("rewards.money")) {
            String moneyRange = config.getString("rewards.money");
            try {
                String[] parts = moneyRange.split("-");
                double minMoney = Double.parseDouble(parts[0]);
                double maxMoney = Double.parseDouble(parts[1]);
                double money = minMoney + (random.nextDouble() * (maxMoney - minMoney));
                return Math.round((money * percentage / 100.0) * 100.0) / 100.0;
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd podczas obliczania nagrody pieniężnej: " + e.getMessage());
                }
            }
        }
        return 0.0;
    }

    private void giveRewardsToPlayer(Player player, List<ItemStack> items, int experience, double money) {
        // Dodaj przedmioty
        for (ItemStack item : items) {
            ItemStack itemToGive = item.clone();
            ItemMeta meta = itemToGive.getItemMeta();
            
            if (meta != null) {
                // Zachowaj wszystkie flagi przedmiotu
                Set<ItemFlag> flags = new HashSet<>(meta.getItemFlags());
                Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
                String displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                List<String> lore = meta.hasLore() ? meta.getLore() : null;
                
                // Ustaw wszystkie zachowane właściwości
                flags.forEach(meta::addItemFlags);
                enchants.forEach((enchant, level) -> meta.addEnchant(enchant, level, true));
                if (displayName != null) meta.setDisplayName(displayName);
                if (lore != null) meta.setLore(lore);
                
                itemToGive.setItemMeta(meta);
            }
            
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(itemToGive);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), itemToGive);
                player.sendMessage(ChatColor.RED + "Twój ekwipunek jest pełny! Przedmiot został upuszczony na ziemię.");
            }
        }
        
        // Dodaj doświadczenie
        player.giveExp(experience);
        
        // Dodaj pieniądze
        if (((Smmetin) plugin).getEconomyManager().isEnabled()) {
            ((Smmetin) plugin).getEconomyManager().addMoney(player, money);
        }
    }

    private boolean checkAndDropTrophy() {
        if (!config.contains("trophy") || !config.getConfigurationSection("trophy").getBoolean("enabled", false)) {
            return false;
        }
        
        ConfigurationSection trophySection = config.getConfigurationSection("trophy");
        double chance = trophySection.getDouble("chance", 0.2);
        
        if (random.nextDouble() <= chance) {
            dropTrophy();
            return true;
        }
        
        return false;
    }

    private void dropTrophy() {
        if (!config.contains("trophy") || !config.getConfigurationSection("trophy").getBoolean("enabled", false)) {
            if (debug) {
                debugLog("Pomijam upuszczenie trofeum - trofeum wyłączone w konfiguracji");
            }
            return;
        }
        
        ConfigurationSection trophySection = config.getConfigurationSection("trophy");
        double chance = trophySection.getDouble("chance", 0.2);
        
        // Sprawdź czy trofeum ma wypaść (losowanie szansy)
        if (random.nextDouble() > chance) {
            if (debug) {
                debugLog("Nie wylosowano upuszczenia trofeum (szansa: " + chance + ")");
            }
            return;
        }
        
        try {
            // Pobierz konfigurację trofeum
            String materialName = trophySection.getString("material", "RED_DYE");
            String name = trophySection.getString("name", "&aOdłamek Metina " + displayName);
            List<String> loreTmp = trophySection.getStringList("lore");
            
            // Stwórz przedmiot trofeum - użyj materiału z konfiguracji (domyślnie RED_DYE)
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                if (debug) {
                    debugLog("Nieprawidłowy materiał w konfiguracji trofeum: " + materialName + ". Używam domyślnego RED_DYE");
                }
                material = Material.RED_DYE;
            }
            
            ItemStack trophy = new ItemStack(material, 1);
            
            // Ustaw metadane przedmiotu bezpośrednio przez Bukkit API
            org.bukkit.inventory.meta.ItemMeta meta = trophy.getItemMeta();
            if (meta != null) {
                // Ustaw nazwę trofeum
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                
                // Formatuj lore zastępując zmienne
                List<String> lore = new ArrayList<>();
                
                // Aktualna data
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd.MM.yyyy");
                String currentDate = dateFormat.format(new java.util.Date());
                
                // Koordynaty zniszczenia
                int x = location.getBlockX();
                int z = location.getBlockZ();
                
                for (String line : loreTmp) {
                    line = line.replace("{date}", currentDate)
                               .replace("{x}", String.valueOf(x))
                               .replace("{z}", String.valueOf(z));
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                
                meta.setLore(lore);
                
                // Dodaj enchant ale ukryj go
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                
                // Ustaw metadane z dodanym enchantem i ukrytymi flagami
                trophy.setItemMeta(meta);
            }
            
            // Upuść przedmiot na miejscu zniszczenia Metina
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), trophy);
            
            // Wyślij wiadomość o upuszczeniu trofeum wszystkim graczom w pobliżu
            String trophyMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.trophy-dropped", 
                    "&aMetin pozostawił po sobie trofeum! &e{name}")
                    .replace("{name}", ChatColor.translateAlternateColorCodes('&', name));
            
            // Wyślij wiadomość wszystkim graczom w promieniu 30 bloków
            location.getWorld().getNearbyEntities(location, 30, 30, 30).forEach(entity -> {
                if (entity instanceof Player) {
                    ((Player) entity).sendMessage(ChatColor.translateAlternateColorCodes('&', trophyMsg));
                }
            });
            
            if (debug) {
                debugLog("Upuszczono trofeum: " + name + " (materiał: " + material + ")");
            }
        } catch (Exception e) {
            if (debug) {
                debugLog("Błąd podczas upuszczania trofeum: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private List<ItemStack> getRewardItems(double percentage) {
        List<ItemStack> items = new ArrayList<>();
        if (config.contains("rewards.items")) {
            List<Map<?, ?>> itemConfigs = config.getMapList("rewards.items");
            for (Map<?, ?> itemConfig : itemConfigs) {
                String material = (String) itemConfig.get("material");
                String amountStr = (String) itemConfig.get("amount");
                int minAmount = 1;
                int maxAmount = 1;
                
                if (amountStr.contains("-")) {
                    String[] parts = amountStr.split("-");
                    minAmount = Integer.parseInt(parts[0].trim());
                    maxAmount = Integer.parseInt(parts[1].trim());
                } else {
                    minAmount = maxAmount = Integer.parseInt(amountStr.trim());
                }

                double chance = ((Number) itemConfig.get("chance")).doubleValue();

                if (random.nextDouble() <= chance) {
                    int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                    ItemStack item = new ItemStack(Material.valueOf(material), amount);
                    ItemMeta meta = item.getItemMeta();
                    
                    if (meta != null) {
                        // Ustaw nazwę
                        if (itemConfig.containsKey("name")) {
                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                                (String) itemConfig.get("name")));
                        }
                        
                        // Ustaw lore dokładnie takie jak w konfiguracji, bez dodatkowych odstępów
                        if (itemConfig.containsKey("lore")) {
                            Object loreObj = itemConfig.get("lore");
                            List<String> lore = new ArrayList<>();
                            
                            if (loreObj instanceof List) {
                                ((List<?>) loreObj).forEach(line -> 
                                    lore.add(ChatColor.translateAlternateColorCodes('&', line.toString())));
                            } else if (loreObj instanceof String) {
                                lore.add(ChatColor.translateAlternateColorCodes('&', (String) loreObj));
                            }
                            
                            meta.setLore(lore);
                        }
                        
                        // Dodaj enchanty
                        if (itemConfig.containsKey("enchantments") && itemConfig.get("enchantments") instanceof Map) {
                            Map<?, ?> enchants = (Map<?, ?>) itemConfig.get("enchantments");
                            enchants.forEach((key, value) -> {
                                if (key instanceof String && value instanceof Number) {
                                    String enchantKey = (String) key;
                                    int level = ((Number) value).intValue();
                                    
                                    for (Enchantment enchantment : Enchantment.values()) {
                                        if (enchantment.getKey().getKey().equalsIgnoreCase(enchantKey)) {
                                            meta.addEnchant(enchantment, level, true);
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                        
                        // Dodaj flagi przedmiotu
                        if (itemConfig.containsKey("item_flags") && itemConfig.get("item_flags") instanceof List) {
                            List<?> flags = (List<?>) itemConfig.get("item_flags");
                            flags.forEach(flag -> {
                                if (flag instanceof String) {
                                    try {
                                        ItemFlag itemFlag = ItemFlag.valueOf((String) flag);
                                        meta.addItemFlags(itemFlag);
                                    } catch (IllegalArgumentException ignored) {
                                        if (debug) {
                                            debugLog("Nieprawidłowa flaga przedmiotu: " + flag);
                                        }
                                    }
                                }
                            });
                        }
                        
                        item.setItemMeta(meta);
                    }
                    
                    items.add(item);
                }
            }
        }
        return items;
    }

    private int calculateExperience(double percentage) {
        if (config.contains("rewards.experience")) {
            String expRange = config.getString("rewards.experience", "0-0");
            try {
                String[] parts = expRange.split("-");
                int minExp = Integer.parseInt(parts[0].trim());
                int maxExp = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : minExp;
                int exp = (int) ((maxExp - minExp + 1) * (percentage / 100.0));
                return exp;
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd podczas obliczania doświadczenia: " + e.getMessage());
                }
            }
        }
        return 0;
    }

    private void spawnMobs() {
        if (config == null) {
            if (debug) {
                debugLog("Brak konfiguracji dla mobów");
            }
            return;
        }
        
        if (debug) {
            debugLog("Próba respienia mobów dla Metina " + id);
        }

        // Pobierz konfigurację mobów bezpośrednio z sekcji Metina
        List<?> mobsList = config.getList("mobs");
        if (mobsList == null || mobsList.isEmpty()) {
            if (debug) {
                debugLog("Lista mobów jest pusta dla Metina: " + id);
                debugLog("Konfiguracja sekcji mobs: " + config.get("mobs"));
            }
            return;
        }

        if (debug) {
            debugLog("Znaleziono " + mobsList.size() + " konfiguracji mobów");
            debugLog("Konfiguracje mobów: " + mobsList);
        }

        for (Object mobObj : mobsList) {
            if (!(mobObj instanceof Map)) continue;
            Map<?, ?> mobConfig = (Map<?, ?>) mobObj;
            
            if (debug) {
                debugLog("Przetwarzanie konfiguracji moba: " + mobConfig);
            }

            String mobType = String.valueOf(mobConfig.get("type"));
            double chance = mobConfig.containsKey("chance") ? 
                ((Number) mobConfig.get("chance")).doubleValue() : 1.0;
            
            if (random.nextDouble() > chance) {
                if (debug) {
                    debugLog("Mob " + mobType + " nie przeszedł testu szansy (" + chance + ")");
                }
                continue;
            }

            // Parsuj zakres ilości mobów
            String amountStr = String.valueOf(mobConfig.get("amount"));
            int minAmount, maxAmount;
            if (amountStr.contains("-")) {
                String[] parts = amountStr.split("-");
                minAmount = Integer.parseInt(parts[0].trim());
                maxAmount = Integer.parseInt(parts[1].trim());
            } else {
                minAmount = maxAmount = Integer.parseInt(amountStr.trim());
            }
            
            int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
            double spawnRadius = mobConfig.containsKey("spawn-radius") ? 
                ((Number) mobConfig.get("spawn-radius")).doubleValue() : 3.0;

            if (debug) {
                debugLog("Próba respienia " + amount + "x " + mobType + " (szansa: " + chance + ", promień: " + spawnRadius + ")");
            }

            spawnMob(mobType, amount, spawnRadius);
        }
    }

    private void spawnMob(String mobType, int amount, double radius) {
        if (debug) {
            debugLog("Próba respienia " + amount + "x " + mobType + " w promieniu " + radius);
        }
        
        for (int i = 0; i < amount; i++) {
            try {
                World world = location.getWorld();
                if (world == null) return;
                
                // Losowa pozycja w promieniu
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * radius;
                double x = location.getX() + distance * Math.cos(angle);
                double z = location.getZ() + distance * Math.sin(angle);
                
                // Znajdź najwyższy blok na tej pozycji
                int blockX = (int) x;
                int blockZ = (int) z;
                int groundY = world.getHighestBlockYAt(blockX, blockZ);
                
                // Upewnij się, że mob będzie respiony 1 blok NAD ziemią, nie w ziemi
                Location spawnLoc = new Location(world, x, groundY + 1, z);
                
                if (debug) {
                    debugLog("Próba spawnu moba na koordynatach: " + 
                            spawnLoc.getX() + ", " + spawnLoc.getY() + ", " + spawnLoc.getZ() + 
                            " (teren na wysokości " + groundY + ")");
                }
                
                // Wykonaj spawn moba w głównym wątku
                final Location finalSpawnLoc = spawnLoc;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        EntityType entityType = EntityType.valueOf(mobType.toUpperCase());
                        Entity entity = world.spawnEntity(finalSpawnLoc, entityType);
                        
                        // Dodaj tag do moba, aby go zidentyfikować
                        entity.addScoreboardTag("metin_mob");
                        entity.addScoreboardTag("metin_" + id);
                        
                        // Ustaw aby mob nie palił się w dzień
                        if (entity instanceof Zombie) {
                            ((Zombie) entity).setShouldBurnInDay(false);
                        } else if (entity instanceof Skeleton) {
                            ((Skeleton) entity).setShouldBurnInDay(false);
                        } else if (entity instanceof PigZombie) {
                            ((PigZombie) entity).setShouldBurnInDay(false);
                        } else if (entity instanceof Phantom) {
                            ((Phantom) entity).setShouldBurnInDay(false);
                        } else if (entity instanceof Drowned) {
                            ((Drowned) entity).setShouldBurnInDay(false);
                        } else if (entity instanceof Stray) {
                            ((Stray) entity).setShouldBurnInDay(false);
                        }
                        
                        // Dodaj moba do listy zrespawnionych mobów
                        spawnedMobs.add((LivingEntity) entity);
                        
                        if (debug) {
                            debugLog("Zrespiono moba typu " + mobType + " na koordynatach: " + 
                                    finalSpawnLoc.getX() + ", " + finalSpawnLoc.getY() + ", " + finalSpawnLoc.getZ());
                        }
                    } catch (IllegalArgumentException e) {
                        if (debug) {
                            debugLog("Błąd przy respie moba, nieprawidłowy typ: " + mobType);
                        }
                    } catch (Exception e) {
                        if (debug) {
                            debugLog("Niespodziewany błąd przy respie moba: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd przy tworzeniu lokalizacji spawnu: " + e.getMessage());
                }
            }
        }
    }

    private Location getRandomLocationAround(double radius) {
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = location.getX() + radius * Math.cos(angle);
        double z = location.getZ() + radius * Math.sin(angle);
        return new Location(location.getWorld(), x, location.getY(), z);
    }

    public boolean isDestroyed() {
        return currentHealth <= 0;
    }

    public Map<UUID, Double> getDamagePercentages() {
        Map<UUID, Double> percentages = new HashMap<>();
        double totalDamage = damageContributors.values().stream().mapToDouble(Double::doubleValue).sum();
        
        damageContributors.forEach((uuid, damage) -> {
            double percentage = (damage / totalDamage) * 100;
            percentages.put(uuid, percentage);
        });
        
        return percentages;
    }

    private void startLifetimeTimer() {
        // Pobierz czas życia z konfiguracji (domyślnie 120 minut, czyli 2 godziny)
        int lifetimeMinutes = config.getRoot().getConfigurationSection("settings").getInt("lifetime", 120);
        long lifetimeMillis = lifetimeMinutes * 60L * 1000L;
        
        if (debug) {
            debugLog("Ustawiam timer czasu życia na " + lifetimeMinutes + " minut");
        }
        
        // Uruchom zadanie, które usunie Metina po upływie czasu życia
        lifetimeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (debug) {
                debugLog("Czas życia Metina " + id + " upłynął. Usuwam...");
            }
            
            // Pobierz instancję MetinManager aby usunąć Metina
            Smmetin smmetinPlugin = (Smmetin) plugin;
            smmetinPlugin.getMetinManager().removeMetin(id);
            
            // Wyślij wiadomość globalna o zniknięciu Metina
            String message = smmetinPlugin.getMetinManager().getMetinsConfig().getString(
                "messages.metin-disappeared", 
                "&7Metin &e{name} &7zniknął po upływie czasu życia!");
            
            message = ChatColor.translateAlternateColorCodes('&', 
                message.replace("{name}", displayName));
            
            Bukkit.broadcastMessage(message);
            
        }, lifetimeMinutes * 60L * 20L); // Konwersja minut na ticki (20 tików = 1 sekunda)
    }

    public void cleanup() {
        if (debug) {
            debugLog("Rozpoczynam czyszczenie Metina " + id);
            debugLog("Liczba bloków lawy do usunięcia: " + lavaBlocks.size());
        }

        // Usuń lawę i przywróć oryginalne bloki
        new HashMap<>(lavaBlocks).forEach((location, originalMaterial) -> {
            Block block = location.getBlock();
            if (block.getType() == Material.LAVA) {
                block.setType(originalMaterial);
                if (debug) {
                    debugLog("Usunięto lawę na lokacji: " + location);
                    debugLog("Przywrócono oryginalny materiał: " + originalMaterial);
                }
            } else if (debug) {
                debugLog("Pominięto blok na lokacji: " + location + " (nie jest lawą)");
            }
            lavaBlocks.remove(location);
        });

        // Zatrzymaj timery
        if (particleTask != null) {
            particleTask.cancel();
        }
        
        if (lifetimeTask != null) {
            lifetimeTask.cancel();
        }

        // Usuń hologramy
        removeHolograms();

        // Usuń moby
        for (LivingEntity mob : new ArrayList<>(spawnedMobs)) {
            if (mob != null && !mob.isDead()) {
                mob.remove();
            }
        }
        spawnedMobs.clear();

        // Przywróć oryginalne bloki
        if (metinBlock != null) {
            metinBlock.setType(originalBlockType);
        }
        if (metinBlockTop != null) {
            metinBlockTop.setType(originalBlockTopType);
        }

        // Wyczyść listę obrażeń
        damageContributors.clear();

        if (debug) {
            debugLog("Zakończono czyszczenie Metina " + id);
        }
    }

    // Gettery
    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Location getLocation() {
        return location;
    }

    public Block getMetinBlock() {
        return metinBlock;
    }
    
    public double getHealth() {
        return currentHealth;
    }
    
    public double getMaxHealth() {
        return maxHealth;
    }

    // Dodaj metodę do sprawdzania czy blok należy do Metina
    public boolean isMetinBlock(Block block) {
        return block != null && (block.equals(metinBlock) || block.equals(metinBlockTop));
    }

    /**
     * Aktualizuje konfigurację Metina
     * @param newConfig Nowa konfiguracja Metina
     */
    public void updateConfig(ConfigurationSection newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
            if (debug) {
                debugLog("Zaktualizowano konfigurację Metina");
            }
        }
    }

    private String formatRewardsList(List<ItemStack> items) {
        return items.stream()
            .map(item -> {
                String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                    ? item.getItemMeta().getDisplayName() 
                    : formatMaterialName(item.getType().name());
                return name + " x" + item.getAmount();
            })
            .collect(Collectors.joining(", "));
    }

    private String formatMaterialName(String materialName) {
        Map<String, String> translations = new HashMap<>();
        translations.put("IRON_INGOT", "Sztabka Żelaza");
        translations.put("GOLD_INGOT", "Sztabka Złota");
        translations.put("DIAMOND", "Diament");
        translations.put("EMERALD", "Szmaragd");
        translations.put("COAL", "Węgiel");
        translations.put("BREAD", "Chleb");
        translations.put("STONE", "Kamień");
        translations.put("COBBLESTONE", "Bruk");
        translations.put("WOODEN_SWORD", "Drewniany Miecz");
        translations.put("STONE_SWORD", "Kamienny Miecz");
        translations.put("IRON_SWORD", "Żelazny Miecz");
        translations.put("GOLDEN_SWORD", "Złoty Miecz");
        translations.put("DIAMOND_SWORD", "Diamentowy Miecz");
        translations.put("BOW", "Łuk");
        translations.put("ARROW", "Strzała");
        translations.put("STICK", "Patyk");
        translations.put("LEATHER", "Skóra");
        translations.put("PAPER", "Papier");
        translations.put("BOOK", "Książka");
        translations.put("APPLE", "Jabłko");
        translations.put("COOKED_BEEF", "Pieczona Wołowina");
        translations.put("COOKED_CHICKEN", "Pieczone Kurczak");
        translations.put("COOKED_PORKCHOP", "Pieczona Wieprzowina");
        translations.put("GOLDEN_APPLE", "Złote Jabłko");
        
        return translations.getOrDefault(materialName, 
            materialName.substring(0, 1).toUpperCase() + 
            materialName.substring(1).toLowerCase().replace("_", " "));
    }
} 