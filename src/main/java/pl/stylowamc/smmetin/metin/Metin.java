package pl.stylowamc.smmetin.metin;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;
import java.util.stream.Collectors;

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

    public Metin(String id, String displayName, Location location, double health, ConfigurationSection config, Plugin plugin) {
        this.id = id;
        this.displayName = displayName;
        this.location = location;
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
        
        if (canCreateMetin()) {
            createMetinBlock();
            createHologram();
            startLifetimeTimer();
            if (debug) {
                debugLog("Utworzono nowego Metina: " + id + " (" + displayName + ") z " + health + " HP");
            }
        } else {
            throw new IllegalStateException("Nie można stworzyć Metina w wodzie!");
        }
    }

    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [Metin:" + id + "] " + message);
        }
    }

    private boolean canCreateMetin() {
        Block block = location.getBlock();
        Block above = block.getRelative(BlockFace.UP);
        
        // Sprawdzamy czy blok lub blok nad nim to woda
        return !block.getType().equals(Material.WATER) && 
               !above.getType().equals(Material.WATER) &&
               !block.isLiquid() && 
               !above.isLiquid();
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
            } else if (debug) {
                debugLog("Nie wylosowano spawnu mobów (szansa 30%)");
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

    private void handleDestroy() {
        if (debug) {
            debugLog("Rozpoczęto proces zniszczenia Metina " + id);
        }
        
        // Efekty zniszczenia
        location.getWorld().createExplosion(location, 0.0f, false, false);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        
        // Wyślij wiadomość o zniszczeniu Metina
        String destroyMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.metin-destroy", 
                "&aMetin &e{name} &azostał zniszczony!")
                .replace("{name}", displayName);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', destroyMsg));
        
        // Oblicz całkowite obrażenia zadane przez wszystkich graczy
        double totalDamage = damageContributors.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Zbierz listę wszystkich uczestników walki
        List<UUID> participants = new ArrayList<>(damageContributors.keySet());
        
        // Przygotuj mapę zawierającą udział procentowy każdego gracza
        Map<UUID, Double> damagePercentages = new HashMap<>();
        for (Map.Entry<UUID, Double> entry : damageContributors.entrySet()) {
            double percentage = (entry.getValue() / totalDamage) * 100.0;
            damagePercentages.put(entry.getKey(), percentage);
        }
        
        // Posortuj graczy według zadanych obrażeń (od najwyższych do najniższych)
        List<Map.Entry<UUID, Double>> sortedContributors = damageContributors.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        // Debug informacja o graczach
        if (debug) {
            debugLog("Zniszczenie Metina - udziały graczy:");
            for (int i = 0; i < sortedContributors.size(); i++) {
                Map.Entry<UUID, Double> entry = sortedContributors.get(i);
                double percentage = (entry.getValue() / totalDamage) * 100.0;
                debugLog("Pozycja " + (i+1) + ": " + Bukkit.getOfflinePlayer(entry.getKey()).getName() + 
                        " - " + entry.getValue() + " dmg (" + String.format("%.2f", percentage) + "%)");
            }
        }
        
        // Upuść trofeum (przed przyznaniem nagród dla graczy)
        dropTrophy();
        
        // Przygotuj listę nagród dla każdego gracza
        Map<UUID, List<ItemStack>> playerRewards = new HashMap<>();
        Map<UUID, Integer> playerExp = new HashMap<>();
        Map<UUID, Double> playerMoney = new HashMap<>();
        
        // Przyznaj nagrody dla wszystkich graczy według ich pozycji na liście
        for (int i = 0; i < sortedContributors.size(); i++) {
            Map.Entry<UUID, Double> entry = sortedContributors.get(i);
            UUID playerId = entry.getKey();
            double damage = entry.getValue();
            double percentage = (damage / totalDamage) * 100.0;
            
            // Określ pozycję gracza dla ustalenia szansy na nagrodę
            int position = i + 1;
            
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // Przekaż informacje o wszystkich uczestnikach do metody giveRewards
                giveRewards(player, percentage, position, participants);
            }
        }
        
        // Usuń blok
        if (metinBlock != null) {
            metinBlock.setType(Material.AIR);
        }
        if (metinBlockTop != null) {
            metinBlockTop.setType(Material.AIR);
        }
        
        // Usuń hologramy i moby
        cleanup();
        
        // Usuń siebie z listy aktywnych Metinów
        if (plugin != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin instanceof Smmetin) {
                    ((Smmetin) plugin).getMetinManager().removeMetin(id);
                }
            });
        }
        
        if (debug) {
            debugLog("Zakończono proces zniszczenia Metina " + id);
        }
    }

    /**
     * Tworzy i upuszcza trofeum na miejscu zniszczenia Metina, jeśli jest włączone w konfiguracji.
     */
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
                
                // Ustaw metadane bez dodawania enchantu (efekt świecenia)
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

    private void giveRewards(Player player, double percentage, int position, List<UUID> allParticipants) {
        if (debug) {
            debugLog("Przyznawanie nagród dla gracza " + player.getName() + 
                    " (pozycja: " + position + ", udział: " + String.format("%.2f", percentage) + "%)");
        }

        // Sprawdź, czy gracz jest online
        if (!player.isOnline()) {
            if (debug) {
                debugLog("Gracz " + player.getName() + " nie jest online, pomijam nagrody");
            }
            return;
        }
        
        // Wyślij informację o zniszczeniu Metina wszystkim uczestnikom
        for (UUID participantId : allParticipants) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                if (participantId.equals(player.getUniqueId())) {
                    continue; // Pomijamy głównego gracza, który odbiera nagrody (otrzyma to później)
                }
                
                // Wyślij informację o graczu, który najbardziej przyczynił się do zniszczenia metina
                if (position == 1) {
                    String topDamagerMsg = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.top-damager",
                            "&7Gracz &e{player} &7zadał najwięcej obrażeń Metinowi i otrzymuje najlepsze nagrody!")
                            .replace("{player}", player.getName())
                            .replace("{metin_name}", displayName);
                    participant.sendMessage(ChatColor.translateAlternateColorCodes('&', topDamagerMsg));
                }
            }
        }
        
        // Określ szansę na nagrodę na podstawie pozycji gracza
        double rewardChance = 1.0; // domyślna szansa na nagrodę
        
        if (position <= 2) {
            // Pierwsze dwie osoby zawsze dostają nagrodę (100%)
            rewardChance = 1.0;
        } else if (position == 3) {
            // Trzecia osoba ma 75% szansy
            rewardChance = 0.75;
        } else if (position == 4) {
            // Czwarta osoba ma 50% szansy
            rewardChance = 0.5;
        } else if (position >= 5 && position <= 10) {
            // 5-10 osoby mają po 25% szansy
            rewardChance = 0.25;
        } else {
            // Pozostali mają 10% szansy
            rewardChance = 0.1;
        }
        
        // Wyślij wiadomość o udziale w zniszczeniu Metina
        String damageMessage = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.damage-percentage",
                "&7Zadałeś &e{percentage}% &7obrażeń Metinowi &e{metin_name}&7 (miejsce: &e{position}&7)")
                .replace("{percentage}", String.format("%.2f", percentage))
                .replace("{metin_name}", displayName)
                .replace("{position}", String.valueOf(position));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', damageMessage));
        
        // Wyślij informację o szansie na nagrodę (jeśli nie jest to 100%)
        if (rewardChance < 1.0) {
            String chanceMessage = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.reward-chance",
                    "&7Ze względu na swoją pozycję ({position}) masz &e{chance}% &7szansy na otrzymanie nagrody")
                    .replace("{position}", String.valueOf(position))
                    .replace("{chance}", String.valueOf((int)(rewardChance * 100)));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', chanceMessage));
        }
        
        // Losowanie czy gracz otrzyma nagrodę (z szansą zależną od pozycji)
        boolean givesReward = position <= 2 || random.nextDouble() <= rewardChance;
        
        if (!givesReward) {
            // Informuj gracza, że nie otrzymał nagrody
            String noRewardMessage = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.no-reward",
                    "&cNie otrzymujesz nagrody - zbyt mały udział w zniszczeniu Metina");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noRewardMessage));
            
            if (debug) {
                debugLog("Gracz " + player.getName() + " (pozycja " + position + ") nie otrzymał nagrody (losowanie)");
            }
            return;
        }
        
        // Oblicz nagrody na podstawie udziału procentowego
        List<ItemStack> items = getRewardItems(percentage);
        
        // Twórz listę przedmiotów które gracz otrzymał (dla wiadomości)
        StringBuilder itemsMessage = new StringBuilder();
        Map<String, Integer> rewardItemsCount = new HashMap<>();
        
        // Dodaj przedmioty do ekwipunku gracza
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(item);
            
            // Dodaj przedmiot do listy otrzymanych przedmiotów
            String itemName = item.getType().toString().toLowerCase().replace("_", " ");
            rewardItemsCount.merge(itemName, item.getAmount(), Integer::sum);
            
            // Jeśli nie można dodać wszystkich przedmiotów do ekwipunku, upuść je na ziemię
            if (!notAdded.isEmpty()) {
                for (ItemStack notAddedItem : notAdded.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), notAddedItem);
                }
            }
        }
        
        // Stwórz wiadomość o otrzymanych przedmiotach
        if (!rewardItemsCount.isEmpty()) {
            int count = 0;
            for (Map.Entry<String, Integer> entry : rewardItemsCount.entrySet()) {
                if (count > 0) {
                    itemsMessage.append("&7, ");
                }
                itemsMessage.append("&e").append(entry.getValue()).append("x ").append(entry.getKey());
                count++;
            }
            
            // Wyświetl wiadomość o otrzymanych przedmiotach
            String itemRewardMessage = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.items-reward", 
                    "&aOtrzymałeś przedmioty: {items}")
                    .replace("{items}", itemsMessage.toString());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', itemRewardMessage));
        }

        // Dodaj doświadczenie
        int experience = calculateExperience(percentage);
        if (experience > 0) {
            player.giveExp(experience);
            // Wyślij wiadomość o otrzymanym doświadczeniu
            String expMessage = ((Smmetin) plugin).getMetinManager().getMetinsConfig().getString("messages.exp-reward",
                    "&aOtrzymałeś &e{amount} EXP &aza zniszczenie Metina!")
                    .replace("{amount}", String.valueOf(experience));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', expMessage));
        }

        // Dodaj pieniądze za pomocą EconomyManager
        try {
            // Pobierz zakres nagród pieniężnych
            String moneyRange = config.getString("rewards.money", "0-0");
            if (!moneyRange.equals("0-0")) {
                // Podziel zakres na min i max
                String[] parts = moneyRange.split("-");
                if (parts.length == 2) {
                    try {
                        double minMoney = Double.parseDouble(parts[0]);
                        double maxMoney = Double.parseDouble(parts[1]);
                        
                        // Oblicz ostateczną kwotę z uwzględnieniem procentowego udziału
                        double moneyAmount = (minMoney + (random.nextDouble() * (maxMoney - minMoney))) * (percentage / 100.0);
                        moneyAmount = Math.round(moneyAmount * 100.0) / 100.0; // Zaokrąglij do 2 miejsc po przecinku
                        
                        // Sprawdź czy EconomyManager jest dostępny i aktywny
                        Smmetin plugin = (Smmetin) this.plugin;
                        if (plugin.getEconomyManager() != null && plugin.getEconomyManager().isEnabled()) {
                            // Dodaj pieniądze graczowi
                            if (plugin.getEconomyManager().addMoney(player, moneyAmount)) {
                                if (debug) {
                                    debugLog("Dodano " + moneyAmount + " do konta gracza " + player.getName());
                                }
                                
                                // Wyślij wiadomość o otrzymanych pieniądzach
                                String currencyName = plugin.getEconomyManager().getCurrencyName(moneyAmount);
                                String moneyMessage = plugin.getMetinManager().getMetinsConfig().getString("messages.money-reward", 
                                        "&aOtrzymałeś &e{amount} {currency} &aza zniszczenie Metina!")
                                        .replace("{amount}", String.valueOf(moneyAmount))
                                        .replace("{currency}", currencyName);
                                player.sendMessage(ChatColor.translateAlternateColorCodes('&', moneyMessage));
                            }
                        } else if (debug) {
                            debugLog("EconomyManager nie jest dostępny lub jest wyłączony");
                        }
                    } catch (NumberFormatException e) {
                        if (debug) {
                            debugLog("Błąd podczas przetwarzania zakresu pieniędzy: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (debug) {
                debugLog("Wystąpił błąd podczas dodawania pieniędzy: " + e.getMessage());
            }
        }
        
        // Aktualizuj statystyki gracza
        Smmetin plugin = (Smmetin) this.plugin;
        plugin.getPlayerDataManager().incrementMetinDestroyed(player, config.getName());
        
        if (debug) {
            debugLog("Nagrody przyznane dla gracza " + player.getName());
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
                    try {
                        ItemStack item = new ItemStack(Material.valueOf(material.toUpperCase()), amount);
                        items.add(item);
                        if (debug) {
                            debugLog("Dodano przedmiot do nagród: " + amount + "x " + material);
                        }
                    } catch (IllegalArgumentException e) {
                        if (debug) {
                            debugLog("Nieprawidłowy materiał: " + material);
                        }
                    }
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
        }

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
                if (debug) {
                    debugLog("Usunięto moba");
                }
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
} 