package pl.stylowamc.smmetin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import pl.stylowamc.smmetin.Smmetin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RewardsGUI implements Listener {
    private final Smmetin plugin;
    private final Map<UUID, String> editingMetin = new HashMap<>();
    private final Map<UUID, EditMode> playerEditMode = new HashMap<>();
    private final Map<UUID, Integer> editingSlot = new HashMap<>();
    private final String MAIN_MENU_TITLE = ChatColor.DARK_PURPLE + "Konfiguracja nagród Metinów";
    private final String REWARDS_MENU_TITLE_PREFIX = ChatColor.DARK_PURPLE + "Nagrody dla Metina: ";
    
    // Tryby edycji
    public enum EditMode {
        NONE,
        AMOUNT,
        CHANCE
    }
    
    public RewardsGUI(Smmetin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    public void openMainMenu(Player player) {
        // Resetuj tryb edycji
        playerEditMode.put(player.getUniqueId(), EditMode.NONE);
        
        ConfigurationSection metinsSection = plugin.getMetinManager().getMetinsConfig().getConfigurationSection("metins");
        if (metinsSection == null) {
            player.sendMessage(ChatColor.RED + "Błąd: Brak sekcji 'metins' w konfiguracji!");
            return;
        }
        
        List<String> metinTypes = new ArrayList<>(metinsSection.getKeys(false));
        int menuSize = ((metinTypes.size() / 9) + 1) * 9;
        menuSize = Math.min(54, Math.max(9, menuSize)); // Między 9 a 54 slotami (1-6 rzędów)
        
        Inventory gui = Bukkit.createInventory(null, menuSize, MAIN_MENU_TITLE);
        
        for (int i = 0; i < metinTypes.size() && i < menuSize; i++) {
            String metinType = metinTypes.get(i);
            ConfigurationSection metinConfig = metinsSection.getConfigurationSection(metinType);
            
            if (metinConfig == null) continue;
            
            Material material;
            try {
                material = Material.valueOf(metinConfig.getString("block-material", "STONE"));
            } catch (IllegalArgumentException e) {
                material = Material.STONE;
            }
            
            String displayName = ChatColor.translateAlternateColorCodes('&', 
                    metinConfig.getString("display-name", "&7" + metinType));
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "ID: " + ChatColor.YELLOW + metinType);
                lore.add(ChatColor.GRAY + "HP: " + ChatColor.RED + metinConfig.getDouble("health", 100));
                lore.add("");
                lore.add(ChatColor.YELLOW + "Kliknij, aby edytować nagrody");
                
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            
            gui.setItem(i, item);
        }
        
        player.openInventory(gui);
    }
    
    public void openRewardsMenu(Player player, String metinType) {
        // Resetuj tryb edycji
        playerEditMode.put(player.getUniqueId(), EditMode.NONE);
        
        // Usuń kody kolorów z ID metina, jeśli istnieją
        String cleanMetinType = ChatColor.stripColor(metinType);
        
        if (debug()) {
            Bukkit.getLogger().info("[SMMetin] Próba otwarcia menu nagród dla Metina typu: " + metinType);
            Bukkit.getLogger().info("[SMMetin] Po usunięciu kolorów: " + cleanMetinType);
            Bukkit.getLogger().info("[SMMetin] Dostępna konfiguracja: " + plugin.getMetinManager().getMetinsConfig().getKeys(true));
        }

        ConfigurationSection metinsSection = plugin.getMetinManager().getMetinsConfig().getConfigurationSection("metins");
        if (metinsSection == null) {
            player.sendMessage(ChatColor.RED + "Błąd: Brak sekcji 'metins' w konfiguracji!");
            if (debug()) {
                Bukkit.getLogger().severe("[SMMetin] Brak sekcji 'metins' w konfiguracji!");
            }
            return;
        }

        // Sprawdź, czy istnieje sekcja dla tego typu Metina (już bez kodów kolorów)
        if (!metinsSection.contains(cleanMetinType)) {
            player.sendMessage(ChatColor.RED + "Błąd: Nie znaleziono konfiguracji dla typu: " + metinType);
            if (debug()) {
                Bukkit.getLogger().severe("[SMMetin] Nie znaleziono typu Metina w konfiguracji: " + metinType);
                Bukkit.getLogger().info("[SMMetin] Dostępne typy: " + metinsSection.getKeys(false));
            }
            return;
        }

        ConfigurationSection metinConfig = metinsSection.getConfigurationSection(cleanMetinType);
        if (metinConfig == null) {
            player.sendMessage(ChatColor.RED + "Błąd: Nie można odczytać konfiguracji dla typu: " + metinType);
            if (debug()) {
                Bukkit.getLogger().severe("[SMMetin] Nie można odczytać konfiguracji dla typu: " + metinType);
            }
            return;
        }
        
        String displayName = ChatColor.translateAlternateColorCodes('&', 
                metinConfig.getString("display-name", "&7" + cleanMetinType));
        
        Inventory gui = Bukkit.createInventory(null, 54, REWARDS_MENU_TITLE_PREFIX + displayName);
        
        // Dodaj instrukcje
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Informacje");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Przeciągnij przedmioty do tego ekwipunku,");
            lore.add(ChatColor.GRAY + "aby dodać je jako nagrody dla Metina.");
            lore.add("");
            lore.add(ChatColor.GRAY + "Kliknij na istniejącą nagrodę, aby edytować");
            lore.add(ChatColor.GRAY + "jej ilość i szansę wypadnięcia.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Po zamknięciu tego ekwipunku,");
            lore.add(ChatColor.YELLOW + "nagrody zostaną zapisane w konfiguracji.");
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(4, info);
        
        // Dodaj linię separującą
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta separatorMeta = separator.getItemMeta();
        if (separatorMeta != null) {
            separatorMeta.setDisplayName(" ");
            separator.setItemMeta(separatorMeta);
        }
        for (int i = 9; i < 18; i++) {
            gui.setItem(i, separator);
        }
        
        // Dodaj istniejące nagrody, jeśli są
        ConfigurationSection rewardsSection = metinConfig.getConfigurationSection("rewards");
        if (rewardsSection != null && rewardsSection.contains("items")) {
            List<Map<?, ?>> existingRewards = rewardsSection.getMapList("items");
            int slot = 18;
            
            for (Map<?, ?> rewardMap : existingRewards) {
                try {
                    String materialName = (String) rewardMap.get("material");
                    String amountStr = (String) rewardMap.get("amount");
                    double chance = ((Number) rewardMap.get("chance")).doubleValue();
                    
                    Material material = Material.valueOf(materialName);
                    int amount = 1;
                    
                    if (amountStr.contains("-")) {
                        String[] parts = amountStr.split("-");
                        amount = Integer.parseInt(parts[0].trim());
                    } else {
                        amount = Integer.parseInt(amountStr.trim());
                    }
                    
                    ItemStack rewardItem = new ItemStack(material, amount);
                    ItemMeta meta = rewardItem.getItemMeta();
                    if (meta != null) {
                        // Ustawienie niestandardowej nazwy, jeśli istnieje
                        if (rewardMap.containsKey("name")) {
                            String customName = (String) rewardMap.get("name");
                            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', customName));
                            
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Ustawiono niestandardową nazwę: " + customName);
                            }
                        }
                        
                        // Dodanie informacji o ilości i szansie do lore
                        List<String> lore = new ArrayList<>();
                        
                        // Dodanie niestandardowego lore, jeśli istnieje
                        if (rewardMap.containsKey("lore")) {
                            Object loreObj = rewardMap.get("lore");
                            if (loreObj instanceof List) {
                                for (Object line : (List<?>) loreObj) {
                                    lore.add(ChatColor.translateAlternateColorCodes('&', line.toString()));
                                }
                            } else if (loreObj instanceof String) {
                                String[] lines = ((String) loreObj).split("\n");
                                for (String line : lines) {
                                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                                }
                            }
                        }
                        
                        // Dodaj informacje o ilości i szansie
                        lore.add(ChatColor.GRAY + "Ilość: " + ChatColor.YELLOW + amountStr);
                        lore.add(ChatColor.GRAY + "Szansa: " + ChatColor.YELLOW + (chance * 100) + "%");
                        lore.add("");
                        lore.add(ChatColor.YELLOW + "Lewy klik: " + ChatColor.GRAY + "Zmień ilość");
                        lore.add(ChatColor.YELLOW + "Prawy klik: " + ChatColor.GRAY + "Zmień szansę");
                        lore.add(ChatColor.YELLOW + "Shift + Prawy klik: " + ChatColor.GRAY + "Usuń nagrodę");
                        
                        meta.setLore(lore);
                        rewardItem.setItemMeta(meta);
                    }
                    
                    // Dodaj enchanty, jeśli istnieją
                    if (rewardMap.containsKey("enchantments") && rewardMap.get("enchantments") instanceof Map) {
                        Map<?, ?> enchants = (Map<?, ?>) rewardMap.get("enchantments");
                        
                        enchants.forEach((key, value) -> {
                            if (key instanceof String && value instanceof Number) {
                                String enchantKey = (String) key;
                                int level = ((Number) value).intValue();
                                
                                // Użyj Bukkit API do odnalezienia enchantu
                                for (Enchantment enchantment : Enchantment.values()) {
                                    if (enchantment.getKey().getKey().equalsIgnoreCase(enchantKey)) {
                                        rewardItem.addUnsafeEnchantment(enchantment, level);
                                        break;
                                    }
                                }
                            }
                        });
                    }

                    // Dodaj flagi przedmiotu, jeśli istnieją
                    if (rewardMap.containsKey("item_flags") && rewardMap.get("item_flags") instanceof List) {
                        List<?> flags = (List<?>) rewardMap.get("item_flags");
                        ItemMeta itemMeta = rewardItem.getItemMeta();
                        if (itemMeta != null) {
                            flags.forEach(flag -> {
                                if (flag instanceof String) {
                                    try {
                                        ItemFlag itemFlag = ItemFlag.valueOf((String) flag);
                                        itemMeta.addItemFlags(itemFlag);
                                    } catch (IllegalArgumentException ignored) {
                                        if (debug()) {
                                            Bukkit.getLogger().warning("[SMMetin] Nieprawidłowa flaga przedmiotu: " + flag);
                                        }
                                    }
                                }
                            });
                            rewardItem.setItemMeta(itemMeta);
                        }
                    }
                    
                    gui.setItem(slot++, rewardItem);
                    
                    if (slot >= 54) break;
                } catch (Exception e) {
                    plugin.getLogger().warning("Błąd wczytywania nagrody: " + e.getMessage());
                    if (debug()) {
                        Bukkit.getLogger().severe("[SMMetin] Błąd podczas wczytywania nagrody: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } else if (debug()) {
            Bukkit.getLogger().info("[SMMetin] Brak nagród lub sekcji rewards.items dla Metina typu: " + metinType);
        }
        
        // Zapisz informację o edytowanym metinie
        editingMetin.put(player.getUniqueId(), metinType);
        
        player.openInventory(gui);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            Inventory clickedInventory = event.getClickedInventory();
            
            if (clickedInventory == null) return;
            
            String title = event.getView().getTitle();
            
            // Obsługa menu głównego
            if (title.equals(MAIN_MENU_TITLE)) {
                event.setCancelled(true);
                
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Kliknięcie w menu głównym: gracz=" + player.getName() + ", slot=" + event.getSlot());
                }
                
                if (event.getClickedInventory() == player.getOpenInventory().getTopInventory()) {
                    ItemStack clickedItem = event.getCurrentItem();
                    if (clickedItem != null && clickedItem.getItemMeta() != null) {
                        List<String> lore = clickedItem.getItemMeta().getLore();
                        if (lore != null && !lore.isEmpty()) {
                            String idLine = lore.get(0);
                            String metinTypeId = idLine.substring(idLine.lastIndexOf(" ") + 1);
                            
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Otwieranie menu nagród dla: " + metinTypeId);
                            }
                            
                            // Otwórz menu nagród dla wybranego metina
                            Bukkit.getScheduler().runTask(plugin, () -> openRewardsMenu(player, metinTypeId));
                        }
                    }
                }
            }
            // Obsługa menu nagród
            else if (title.startsWith(REWARDS_MENU_TITLE_PREFIX)) {
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Kliknięcie w menu nagród: gracz=" + player.getName() + 
                                          ", slot=" + event.getSlot() + 
                                          ", akcja=" + event.getAction() + 
                                          ", lewy klik=" + event.isLeftClick() + 
                                          ", prawy klik=" + event.isRightClick() + 
                                          ", przedmiot=" + (event.getCurrentItem() != null ? event.getCurrentItem().getType() : "null"));
                }
                
                String metinType = extractMetinTypeFromTitle(title);
                if (metinType != null && !metinType.isEmpty()) {
                    // Aktualizuj metinType w mapie dla gracza
                    editingMetin.put(player.getUniqueId(), metinType);
                    if (debug()) {
                        Bukkit.getLogger().info("[SMMetin] Zapisano typ metina z tytułu: " + metinType);
                    }
                }
                
                // Kliknięcie w inwentarz Metina (górny)
                if (event.getClickedInventory() == player.getOpenInventory().getTopInventory()) {
                    // Blokuj klikanie w górną część menu (informacje i separator)
                    if (event.getSlot() < 18) {
                        event.setCancelled(true);
                    } 
                    // Obsługa kliknięcia na nagrodę (sloty 18+)
                    else if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                        // Obsługa usuwania nagrody przez Shift + Prawy przycisk
                        if (event.isShiftClick() && event.isRightClick()) {
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Usuwanie nagrody: slot=" + event.getSlot());
                            }
                            event.getInventory().setItem(event.getSlot(), null);
                            event.setCancelled(true);
                            return;
                        }
                        
                        // Zatrzymaj standardowe przenoszenie przedmiotu i zamiast tego rozpocznij edycję
                        event.setCancelled(true);
                        
                        int slot = event.getSlot();
                        ItemStack item = event.getCurrentItem();
                        
                        if (debug()) {
                            Bukkit.getLogger().info("[SMMetin] Edycja przedmiotu: slot=" + slot + 
                                                  ", typ=" + item.getType() + 
                                                  ", nazwa=" + (item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : "brak") + 
                                                  ", ilość=" + item.getAmount());
                        }
                        
                        // Zapisz slot, który jest edytowany
                        editingSlot.put(player.getUniqueId(), slot);
                        String currentMetinType = editingMetin.get(player.getUniqueId());
                        
                        if (debug()) {
                            Bukkit.getLogger().info("[SMMetin] Zapisany typ metina przy edycji: " + currentMetinType);
                        }
                        
                        // Sprawdź czy lewy czy prawy przycisk myszy
                        if (event.isLeftClick()) {
                            // Edycja ilości
                            playerEditMode.put(player.getUniqueId(), EditMode.AMOUNT);
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Rozpoczęcie edycji ilości: gracz=" + player.getName() + 
                                                      ", metin=" + currentMetinType + 
                                                      ", slot=" + slot);
                            }
                            player.closeInventory(); // Zamknij ekwipunek aby umożliwić wpisanie tekstu
                            player.sendMessage(ChatColor.GREEN + "Wpisz ilość przedmiotu (np. \"1-3\" lub \"5\"). Wpisz \"0\" aby usunąć przedmiot:");
                        } else if (event.isRightClick()) {
                            // Edycja szansy
                            playerEditMode.put(player.getUniqueId(), EditMode.CHANCE);
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Rozpoczęcie edycji szansy: gracz=" + player.getName() + 
                                                      ", metin=" + currentMetinType + 
                                                      ", slot=" + slot);
                            }
                            player.closeInventory(); // Zamknij ekwipunek aby umożliwić wpisanie tekstu
                            player.sendMessage(ChatColor.GREEN + "Wpisz szansę wypadnięcia w procentach (1-100):");
                        }
                    }
                } 
                // Obsługa przeciągania przedmiotów z ekwipunku gracza
                else if (event.getClickedInventory() == player.getInventory()) {
                    // Kiedy gracz kładzie przedmiot do górnego inwentarza, zapamiętaj przedmiot i jego typ
                    if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                        ItemStack item = event.getCurrentItem().clone(); // Klonuj, aby uniknąć modyfikacji oryginału
                        
                        // Dodaj domyślne lore z informacjami o ilości i szansie
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            List<String> lore = new ArrayList<>();
                            lore.add(ChatColor.GRAY + "Ilość: " + ChatColor.YELLOW + "1-1");
                            lore.add(ChatColor.GRAY + "Szansa: " + ChatColor.YELLOW + "1.0%");
                            lore.add("");
                            lore.add(ChatColor.YELLOW + "Lewy klik: " + ChatColor.GRAY + "Zmień ilość");
                            lore.add(ChatColor.YELLOW + "Prawy klik: " + ChatColor.GRAY + "Zmień szansę");
                            meta.setLore(lore);
                            item.setItemMeta(meta);
                        }
                        
                        if (debug()) {
                            Bukkit.getLogger().info("[SMMetin] Gracz przenosi przedmiot: " + item.getType());
                        }
                    }
                }
                
                // Dodaj obsługę przenoszenia przedmiotów
                if (event.getAction().toString().contains("PLACE") && 
                    player.getOpenInventory().getTopInventory() == event.getInventory() && 
                    event.getSlot() >= 18) {
                    
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Uruchom po zakończeniu akcji, aby mieć dostęp do zaktualizowanego inwentarza
                        ItemStack placedItem = event.getInventory().getItem(event.getSlot());
                        
                        if (placedItem != null && placedItem.getType() != Material.AIR) {
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Dodano przedmiot do slotu " + event.getSlot() + ": " + placedItem.getType());
                            }
                            
                            ItemMeta meta = placedItem.getItemMeta();
                            if (meta != null) {
                                List<String> lore = new ArrayList<>();
                                
                                // Zachowaj oryginalne lore wraz z pustymi liniami
                                if (meta.hasLore()) {
                                    List<String> originalLore = meta.getLore();
                                    for (String line : originalLore) {
                                        if (!line.startsWith(ChatColor.GRAY + "Ilość: ") &&
                                            !line.startsWith(ChatColor.GRAY + "Szansa: ") &&
                                            !line.startsWith(ChatColor.YELLOW + "Lewy klik: ") &&
                                            !line.startsWith(ChatColor.YELLOW + "Prawy klik: ") &&
                                            !line.startsWith(ChatColor.YELLOW + "Shift + Prawy klik: ")) {
                                            lore.add(line);
                                        }
                                    }
                                    
                                    // Usuń ostatnią pustą linię, jeśli istnieje
                                    while (!lore.isEmpty() && lore.get(lore.size() - 1).isEmpty()) {
                                        lore.remove(lore.size() - 1);
                                    }
                                }
                                
                                // Zachowaj enchanty i ich widoczność
                                Map<Enchantment, Integer> enchants = new HashMap<>(placedItem.getEnchantments());
                                Set<ItemFlag> flags = new HashSet<>(meta.getItemFlags());
                                
                                // Dodaj informacje o edycji
                                lore.add(ChatColor.GRAY + "Ilość: " + ChatColor.YELLOW + "1-1");
                                lore.add(ChatColor.GRAY + "Szansa: " + ChatColor.YELLOW + "1.0%");
                                lore.add("");
                                lore.add(ChatColor.YELLOW + "Lewy klik: " + ChatColor.GRAY + "Zmień ilość");
                                lore.add(ChatColor.YELLOW + "Prawy klik: " + ChatColor.GRAY + "Zmień szansę");
                                lore.add(ChatColor.YELLOW + "Shift + Prawy klik: " + ChatColor.GRAY + "Usuń nagrodę");
                                
                                // Ustaw wszystkie właściwości z powrotem
                                meta.setLore(lore);
                                placedItem.setItemMeta(meta);
                                
                                // Przywróć enchanty i flagi
                                placedItem.addUnsafeEnchantments(enchants);
                                ItemMeta updatedMeta = placedItem.getItemMeta();
                                if (updatedMeta != null) {
                                    flags.forEach(updatedMeta::addItemFlags);
                                    placedItem.setItemMeta(updatedMeta);
                                }
                            }
                        }
                    }, 1L);
                }
            }
        }
    }
    
    // Pomocnicza metoda do ekstrahowania typu metina z tytułu menu
    private String extractMetinTypeFromTitle(String title) {
        if (title.startsWith(REWARDS_MENU_TITLE_PREFIX)) {
            String metinName = title.substring(REWARDS_MENU_TITLE_PREFIX.length());
            if (debug()) {
                Bukkit.getLogger().info("[SMMetin] Wyekstrahowano nazwę metina: " + metinName);
            }
            
            // Szukaj odpowiadającego metina w konfiguracji na podstawie display-name
            ConfigurationSection metinsSection = plugin.getMetinManager().getMetinsConfig().getConfigurationSection("metins");
            if (metinsSection != null) {
                for (String metinType : metinsSection.getKeys(false)) {
                    ConfigurationSection metinConfig = metinsSection.getConfigurationSection(metinType);
                    if (metinConfig != null) {
                        String displayName = ChatColor.translateAlternateColorCodes('&', 
                                metinConfig.getString("display-name", "&7" + metinType));
                        if (metinName.equals(displayName)) {
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Znaleziono pasujący typ metina: " + metinType);
                            }
                            return metinType;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Sprawdź czy gracz jest w trybie edycji
        if (playerEditMode.containsKey(playerId) && playerEditMode.get(playerId) != EditMode.NONE) {
            event.setCancelled(true); // Anuluj wiadomość na czacie
            String input = event.getMessage();
            
            if (debug()) {
                Bukkit.getLogger().info("[SMMetin] Odebrano wiadomość do edycji: " + input + 
                                      ", gracz=" + player.getName() + 
                                      ", tryb=" + playerEditMode.get(playerId));
            }
            
            // Sprawdź anulowanie edycji
            if (input.equalsIgnoreCase("anuluj") || input.equalsIgnoreCase("cancel")) {
                playerEditMode.put(playerId, EditMode.NONE);
                player.sendMessage(ChatColor.YELLOW + "Anulowano edycję.");
                
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Gracz anulował edycję: " + player.getName());
                }
                
                // Otwórz z powrotem menu
                if (editingMetin.containsKey(playerId)) {
                    String metinType = editingMetin.get(playerId);
                    Bukkit.getScheduler().runTask(plugin, () -> reopenRewardsMenu(player, metinType));
                }
                return;
            }
            
            // Pobierz informacje o edytowanym przedmiocie
            if (editingMetin.containsKey(playerId) && editingSlot.containsKey(playerId)) {
                String metinType = editingMetin.get(playerId);
                String cleanMetinType = ChatColor.stripColor(metinType);
                int slot = editingSlot.get(playerId);
                EditMode currentMode = playerEditMode.get(playerId);
                
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Dane edycji: metin=" + metinType + 
                                          ", cleanMetinType=" + cleanMetinType + 
                                          ", slot=" + slot + 
                                          ", tryb=" + currentMode);
                }
                
                // Wyłącz tryb edycji przed synchronizacją, aby uniknąć konfliktów
                playerEditMode.put(playerId, EditMode.NONE);
                
                // Synchronizuj z głównym wątkiem serwera
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        // Pobierz aktualną konfigurację bezpośrednio z pliku
                        File metinsFile = new File(plugin.getDataFolder(), "metins.yml");
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(metinsFile);
                        String path = "metins." + cleanMetinType + ".rewards.items";
                        
                        if (debug()) {
                            Bukkit.getLogger().info("[SMMetin] Ścieżka do nagród: " + path);
                            Bukkit.getLogger().info("[SMMetin] Istnieje sekcja metina: " + config.contains("metins." + cleanMetinType));
                            Bukkit.getLogger().info("[SMMetin] Istnieje ścieżka: " + config.contains(path));
                        }
                        
                        // Upewnij się, że ścieżka istnieje w konfiguracji
                        if (!config.contains("metins." + cleanMetinType)) {
                            player.sendMessage(ChatColor.RED + "Błąd: Nie znaleziono sekcji Metina w konfiguracji");
                            if (debug()) {
                                Bukkit.getLogger().severe("[SMMetin] Brak sekcji metina: " + cleanMetinType);
                                Bukkit.getLogger().info("[SMMetin] Dostępne sekcje: " + config.getConfigurationSection("metins").getKeys(false));
                            }
                            return;
                        }

                        List<Map<String, Object>> rewards = new ArrayList<>();
                        
                        // Pobierz obecne nagrody z konfiguracji
                        if (config.contains(path)) {
                            List<Map<?, ?>> existingRewards = config.getMapList(path);
                            
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Znaleziono " + existingRewards.size() + " istniejących nagród");
                                for (int i = 0; i < existingRewards.size(); i++) {
                                    Bukkit.getLogger().info("[SMMetin] Istniejąca nagroda " + (i+1) + ": " + existingRewards.get(i));
                                }
                            }
                            
                            // Konwertuj istniejące nagrody do nowej listy
                            for (Map<?, ?> oldReward : existingRewards) {
                                Map<String, Object> newReward = new HashMap<>();
                                for (Map.Entry<?, ?> entry : oldReward.entrySet()) {
                                    if (entry.getKey() instanceof String) {
                                        newReward.put((String) entry.getKey(), entry.getValue());
                                    }
                                }
                                rewards.add(newReward);
                            }
                        } else {
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Brak istniejących nagród, tworzę nową listę");
                            }
                        }
                        
                        // Upewnij się, że mamy wystarczająco dużo elementów w liście
                        int rewardIndex = slot - 18; // Pierwszy slot nagród to 18
                        while (rewards.size() <= rewardIndex) {
                            Map<String, Object> emptyReward = new HashMap<>();
                            rewards.add(emptyReward);
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Dodaję pustą nagrodę na indeks " + (rewards.size() - 1));
                            }
                        }
                        
                        // Pobierz bieżącą nagrodę do edycji
                        Map<String, Object> currentReward = rewards.get(rewardIndex);
                        
                        if (debug()) {
                            Bukkit.getLogger().info("[SMMetin] Nagroda do edycji (indeks " + rewardIndex + "): " + currentReward);
                        }
                        
                        // Sprawdź, czy nagroda zawiera materiał - jeśli nie, nie możemy kontynuować
                        if (!currentReward.containsKey("material")) {
                            player.sendMessage(ChatColor.RED + "Błąd: Przedmiot nie ma zdefiniowanego typu (material)");
                            if (debug()) {
                                Bukkit.getLogger().severe("[SMMetin] Nie można edytować nagrody bez materiału na indeksie " + rewardIndex);
                            }
                            reopenRewardsMenu(player, metinType);
                            return;
                        }
                        
                        // Aktualizuj odpowiednie pole nagrody
                        if (currentMode == EditMode.AMOUNT) {
                            // Walidacja formatu ilości
                            String amountStr = input;
                            
                            // Specjalna obsługa dla ilości równej 0
                            if (amountStr.trim().equals("0")) {
                                if (debug()) {
                                    Bukkit.getLogger().info("[SMMetin] Ustawiono ilość 0 - usuwam przedmiot");
                                }
                                // Usuń nagrodę
                                rewards.remove(rewardIndex);
                                player.sendMessage(ChatColor.GREEN + "Nagroda została usunięta.");
                            } else {
                                // Standardowa walidacja ilości
                                if (!isValidAmountFormat(amountStr)) {
                                    player.sendMessage(ChatColor.RED + "Nieprawidłowy format! Użyj formatu \"1-3\" lub \"5\".");
                                    if (debug()) {
                                        Bukkit.getLogger().warning("[SMMetin] Nieprawidłowy format ilości: " + amountStr);
                                    }
                                    reopenRewardsMenu(player, metinType);
                                    return;
                                }
                                
                                currentReward.put("amount", amountStr);
                                if (debug()) {
                                    Bukkit.getLogger().info("[SMMetin] Ustawiono ilość na: " + amountStr);
                                }
                                player.sendMessage(ChatColor.GREEN + "Ustawiono ilość: " + amountStr);
                            }
                        } else if (currentMode == EditMode.CHANCE) {
                            // Walidacja szansy
                            double chance;
                            try {
                                chance = Double.parseDouble(input);
                                if (chance < 0 || chance > 100) {
                                    player.sendMessage(ChatColor.RED + "Szansa musi być między 0 a 100!");
                                    if (debug()) {
                                        Bukkit.getLogger().warning("[SMMetin] Nieprawidłowa wartość szansy: " + chance);
                                    }
                                    reopenRewardsMenu(player, metinType);
                                    return;
                                }
                                // Konwertuj procenty na wartość 0-1
                                chance = chance / 100.0;
                            } catch (NumberFormatException e) {
                                player.sendMessage(ChatColor.RED + "Nieprawidłowy format liczby!");
                                if (debug()) {
                                    Bukkit.getLogger().warning("[SMMetin] Nie można sparsować szansy: " + input);
                                }
                                reopenRewardsMenu(player, metinType);
                                return;
                            }
                            
                            currentReward.put("chance", chance);
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Ustawiono szansę na: " + (chance * 100) + "%");
                            }
                            player.sendMessage(ChatColor.GREEN + "Ustawiono szansę: " + (chance * 100) + "%");
                        }
                        
                        // Zapisz zmiany w konfiguracji
                        config.set(path, rewards);
                        try {
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Zapisuję " + rewards.size() + " nagród do pliku");
                                for (int i = 0; i < rewards.size(); i++) {
                                    Bukkit.getLogger().info("[SMMetin] Nagroda " + (i+1) + " do zapisu: " + rewards.get(i));
                                }
                            }
                            
                            config.save(metinsFile);
                            
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Nagrody zostały zapisane do pliku");
                                // Sprawdź, czy zapisano poprawnie
                                YamlConfiguration checkConfig = YamlConfiguration.loadConfiguration(metinsFile);
                                List<Map<?, ?>> savedRewards = checkConfig.getMapList(path);
                                Bukkit.getLogger().info("[SMMetin] Po zapisie znaleziono " + savedRewards.size() + " nagród");
                            }
                            
                            // Odśwież konfigurację bez wyświetlania logów
                            // Tymczasowo wyłącz logi debug w MetinManager
                            boolean previousDebug = plugin.getMetinManager().isDebug();
                            plugin.getMetinManager().setDebug(false);
                            plugin.getMetinManager().reloadConfig();
                            plugin.getMetinManager().setDebug(previousDebug);
                            
                            if (debug()) {
                                Bukkit.getLogger().info("[SMMetin] Konfiguracja została przeładowana");
                            }
                            
                            // Ponownie otwórz menu nagród
                            reopenRewardsMenu(player, metinType);
                        } catch (IOException e) {
                            player.sendMessage(ChatColor.RED + "Błąd podczas zapisywania: " + e.getMessage());
                            if (debug()) {
                                Bukkit.getLogger().severe("[SMMetin] Błąd podczas zapisywania: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        player.sendMessage(ChatColor.RED + "Wystąpił błąd podczas edycji nagrody: " + e.getMessage());
                        if (debug()) {
                            Bukkit.getLogger().severe("[SMMetin] Błąd podczas edycji nagrody: " + e.getMessage());
                            e.printStackTrace();
                        }
                        reopenRewardsMenu(player, metinType);
                    }
                });
            } else {
                player.sendMessage(ChatColor.RED + "Wystąpił błąd podczas edycji nagrody.");
                if (debug()) {
                    Bukkit.getLogger().severe("[SMMetin] Brak danych o edytowanym przedmiocie: metinType=" + 
                            (editingMetin.containsKey(playerId) ? editingMetin.get(playerId) : "null") + 
                            ", slot=" + (editingSlot.containsKey(playerId) ? editingSlot.get(playerId) : "null"));
                }
                playerEditMode.put(playerId, EditMode.NONE);
            }
        }
    }
    
    private boolean isValidAmountFormat(String amountStr) {
        if (amountStr.contains("-")) {
            // Format zakresowy (np. "1-3")
            String[] parts = amountStr.split("-");
            if (parts.length != 2) return false;
            
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return min > 0 && max >= min;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            // Format pojedynczej wartości
            try {
                int amount = Integer.parseInt(amountStr.trim());
                return amount > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
    
    private void reopenRewardsMenu(Player player, String metinType) {
        // Resetuj tryb edycji
        playerEditMode.put(player.getUniqueId(), EditMode.NONE);
        
        // Otwórz menu z powrotem po krótkim opóźnieniu
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openRewardsMenu(player, metinType);
        }, 2L);
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerId = player.getUniqueId();
            String title = event.getView().getTitle();
            
            // Jeśli gracz jest w trybie edycji, nie zapisuj inwentarza
            if (playerEditMode.containsKey(playerId) && 
                playerEditMode.get(playerId) != EditMode.NONE) {
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Pominięto zapis przy zamknięciu - gracz jest w trybie edycji: " + 
                                           playerEditMode.get(playerId));
                }
                return;
            }
            
            if (title.startsWith(REWARDS_MENU_TITLE_PREFIX) && editingMetin.containsKey(playerId)) {
                String metinType = editingMetin.get(playerId);
                
                if (metinType == null || metinType.isEmpty()) {
                    metinType = extractMetinTypeFromTitle(title);
                    if (debug()) {
                        Bukkit.getLogger().info("[SMMetin] Próba wyodrębnienia typu metina z tytułu: " + metinType);
                    }
                }
                
                if (metinType == null || metinType.isEmpty()) {
                    if (debug()) {
                        Bukkit.getLogger().severe("[SMMetin] Brak informacji o typie metina przy zapisie! Tytuł: " + title);
                    }
                    player.sendMessage(ChatColor.RED + "Błąd: Nie można ustalić typu Metina do zapisania nagród");
                    return;
                }
                
                // Usuń kody kolorów z ID metina przy zapisie
                String cleanMetinType = ChatColor.stripColor(metinType);
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Zapisywanie nagród dla metina: " + cleanMetinType + 
                                          " (przed usunięciem kolorów: " + metinType + ")");
                }
                
                saveRewards(player, cleanMetinType, event.getInventory());
                editingMetin.remove(playerId);
            }
        }
    }
    
    private void saveRewards(Player player, String metinType, Inventory inventory) {
        if (debug()) {
            Bukkit.getLogger().info("[SMMetin] Zapisywanie nagród dla: " + metinType);
        }
        
        List<Map<String, Object>> rewards = new ArrayList<>();
        
        for (int i = 18; i < 54; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                Map<String, Object> rewardMap = new HashMap<>();
                ItemMeta meta = item.getItemMeta();
                
                if (meta != null) {
                    // Zapisz podstawowe informacje
                    rewardMap.put("material", item.getType().name());
                    
                    // Zapisz nazwę i lore
                    if (meta.hasDisplayName()) {
                        rewardMap.put("name", meta.getDisplayName());
                    }
                    
                    if (meta.hasLore()) {
                        List<String> customLore = new ArrayList<>();
                        
                        for (String line : meta.getLore()) {
                            if (!line.startsWith(ChatColor.GRAY + "Ilość: ") &&
                                !line.startsWith(ChatColor.GRAY + "Szansa: ") &&
                                !line.startsWith(ChatColor.YELLOW + "Lewy klik: ") &&
                                !line.startsWith(ChatColor.YELLOW + "Prawy klik: ") &&
                                !line.startsWith(ChatColor.YELLOW + "Shift + Prawy klik: ")) {
                                customLore.add(line);
                            }
                        }
                        
                        // Usuń ostatnią pustą linię, jeśli istnieje
                        while (!customLore.isEmpty() && customLore.get(customLore.size() - 1).isEmpty()) {
                            customLore.remove(customLore.size() - 1);
                        }
                        
                        if (!customLore.isEmpty()) {
                            rewardMap.put("lore", customLore);
                        }
                    }
                    
                    // Zapisz wszystkie flagi przedmiotu
                    Set<ItemFlag> flags = meta.getItemFlags();
                    if (!flags.isEmpty()) {
                        List<String> flagsList = flags.stream()
                                .map(ItemFlag::name)
                                .collect(Collectors.toList());
                        rewardMap.put("item_flags", flagsList);
                    }
                    
                    // Zapisz enchanty
                    Map<Enchantment, Integer> enchants = item.getEnchantments();
                    if (!enchants.isEmpty()) {
                        Map<String, Integer> enchantsMap = new HashMap<>();
                        enchants.forEach((enchantment, level) -> {
                            enchantsMap.put(enchantment.getKey().getKey(), level);
                        });
                        rewardMap.put("enchantments", enchantsMap);
                        
                        // Dodaj flagę HIDE_ENCHANTS, jeśli enchanty są ukryte
                        if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
                            if (!rewardMap.containsKey("item_flags")) {
                                rewardMap.put("item_flags", new ArrayList<>());
                            }
                            ((List<String>) rewardMap.get("item_flags")).add(ItemFlag.HIDE_ENCHANTS.name());
                        }
                    }
                    
                    // Pobierz ilość i szansę z lore
                    if (meta.hasLore()) {
                        for (String line : meta.getLore()) {
                            if (line.startsWith(ChatColor.GRAY + "Ilość: ")) {
                                String amountStr = ChatColor.stripColor(line).substring(7).trim();
                                rewardMap.put("amount", amountStr);
                            } else if (line.startsWith(ChatColor.GRAY + "Szansa: ")) {
                                String chanceStr = ChatColor.stripColor(line).substring(8).trim();
                                chanceStr = chanceStr.replace("%", "").trim();
                                try {
                                    double chance = Double.parseDouble(chanceStr) / 100.0;
                                    rewardMap.put("chance", chance);
                                } catch (NumberFormatException e) {
                                    rewardMap.put("chance", 0.01);
                                }
                            }
                        }
                    }
                    
                    // Dodaj domyślne wartości, jeśli nie znaleziono w lore
                    if (!rewardMap.containsKey("amount")) {
                        rewardMap.put("amount", "1-1");
                    }
                    if (!rewardMap.containsKey("chance")) {
                        rewardMap.put("chance", 0.01);
                    }
                }
                
                rewards.add(rewardMap);
            }
        }
        
        // Zapisz nagrody do konfiguracji
        ConfigurationSection metinConfig = plugin.getMetinManager().getMetinsConfig()
                .getConfigurationSection("metins." + metinType);
        
        if (metinConfig != null) {
            metinConfig.set("rewards.items", rewards);
            try {
                plugin.getMetinManager().getMetinsConfig().save(plugin.getMetinManager().getMetinsFile());
                if (debug()) {
                    Bukkit.getLogger().info("[SMMetin] Pomyślnie zapisano nagrody dla Metina: " + metinType);
                }
            } catch (IOException e) {
                Bukkit.getLogger().severe("[SMMetin] Błąd podczas zapisywania nagród dla Metina " + metinType + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    // Pomocnicza metoda do sprawdzania, czy debug jest włączony
    private boolean debug() {
        return plugin.getMetinManager().getMetinsConfig().getConfigurationSection("settings").getBoolean("debug", true);
    }
} 