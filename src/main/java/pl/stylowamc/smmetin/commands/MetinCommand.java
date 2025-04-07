package pl.stylowamc.smmetin.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.stylowamc.smmetin.Smmetin;
import pl.stylowamc.smmetin.gui.RewardsGUI;
import pl.stylowamc.smmetin.manager.MetinManager;
import pl.stylowamc.smmetin.manager.PlayerDataManager;
import pl.stylowamc.smmetin.metin.Metin;

import java.util.*;
import java.util.stream.Collectors;

public class MetinCommand implements CommandExecutor, TabCompleter {
    private final Smmetin plugin;
    private final MetinManager metinManager;
    private final PlayerDataManager playerDataManager;
    private final List<String> metinTypes;
    private final RewardsGUI rewardsGUI;

    public MetinCommand(Smmetin plugin) {
        this.plugin = plugin;
        this.metinManager = plugin.getMetinManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.rewardsGUI = new RewardsGUI(plugin);
        
        // Pobierz listę typów Metinów z konfiguracji
        this.metinTypes = new ArrayList<>(plugin.getMetinManager().getMetinsConfig()
                .getConfigurationSection("metins").getKeys(false));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                return handleSpawnCommand(sender, args);
            case "tp":
                return handleTeleportCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "remove":
                return handleRemoveCommand(sender, args);
            case "removeall":
                return handleRemoveAllCommand(sender);
            case "statystyki":
                return handleStatsCommand(sender, args);
            case "topka":
                return handleTopCommand(sender, args);
            case "rewards":
                return handleRewardsCommand(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleSpawnCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smmetin.spawn")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /metin spawn <typ>");
            return true;
        }

        String metinType = args[1].toLowerCase();
        metinManager.forceSpawnMetin(metinType);
        sender.sendMessage(ChatColor.GREEN + "Wymuszono spawn Metina typu: " + metinType);
        return true;
    }

    private boolean handleTeleportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być użyta tylko przez gracza!");
            return true;
        }

        if (!sender.hasPermission("smmetin.teleport")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /metin tp <id>");
            return true;
        }

        Player player = (Player) sender;
        String metinId = args[1];
        boolean success = metinManager.teleportPlayerToMetin(player, metinId);

        if (!success) {
            sender.sendMessage(ChatColor.RED + "Nie znaleziono Metina o ID: " + metinId);
        }

        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("smmetin.list")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }

        Collection<Metin> activeMetins = metinManager.getActiveMetins();
        
        if (activeMetins.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Nie ma obecnie aktywnych Metinów.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Aktywne Metiny (" + activeMetins.size() + "):");
        for (Metin metin : activeMetins) {
            sender.sendMessage(
                ChatColor.YELLOW + "- " + metin.getId() + 
                ChatColor.GRAY + " | " + ChatColor.RESET + metin.getDisplayName() + 
                ChatColor.GRAY + " | " + String.format("%.1f", metin.getHealth()) + "/" + metin.getMaxHealth() + " HP" +
                ChatColor.GRAY + " | " + "X: " + (int) metin.getLocation().getX() + 
                ", Y: " + (int) metin.getLocation().getY() + 
                ", Z: " + (int) metin.getLocation().getZ()
            );
        }

        return true;
    }

    private boolean handleRemoveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smmetin.remove")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Użycie: /metin remove <id>");
            return true;
        }

        String metinId = args[1];
        Metin metin = metinManager.getMetin(metinId);

        if (metin == null) {
            sender.sendMessage(ChatColor.RED + "Nie znaleziono Metina o ID: " + metinId);
            return true;
        }

        metin.cleanup();
        metinManager.removeMetin(metinId);
        sender.sendMessage(ChatColor.GREEN + "Usunięto Metina o ID: " + metinId);
        return true;
    }

    private boolean handleRemoveAllCommand(CommandSender sender) {
        if (!sender.hasPermission("smmetin.removeall")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }

        metinManager.removeAllMetins();
        sender.sendMessage(ChatColor.GREEN + "Usunięto wszystkie aktywne Metiny.");
        return true;
    }
    
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Konsola musi podać nazwę gracza: /metin statystyki <gracz>");
            return true;
        }
        
        // Jeśli podano nazwę gracza, pobierz statystyki dla tego gracza
        Player targetPlayer;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza o nazwie: " + args[1]);
                return true;
            }
            
            // Sprawdź uprawnienia, jeśli gracz chce zobaczyć statystyki innego gracza
            if (sender instanceof Player && !sender.equals(targetPlayer) && !sender.hasPermission("smmetin.stats.others")) {
                sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do przeglądania statystyk innych graczy!");
                return true;
            }
        } else {
            targetPlayer = (Player) sender;
        }
        
        // Pobierz statystyki
        UUID playerUUID = targetPlayer.getUniqueId();
        Map<String, Integer> stats = playerDataManager.getPlayerMetinStats(playerUUID);
        int totalDestroyed = playerDataManager.getTotalDestroyed(playerUUID);
        
        // Wyświetl statystyki
        sender.sendMessage(ChatColor.GREEN + "Statystyki zniszczonych Metinów dla gracza " + 
                ChatColor.YELLOW + targetPlayer.getName() + ChatColor.GREEN + ":");
        sender.sendMessage(ChatColor.GRAY + "Łącznie zniszczonych Metinów: " + ChatColor.GOLD + totalDestroyed);
        
        if (stats.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Gracz nie zniszczył jeszcze żadnych Metinów.");
        } else {
            sender.sendMessage(ChatColor.GRAY + "Szczegółowe statystyki:");
            
            // Sortuj statystyki według liczby zniszczonych Metinów (malejąco)
            stats.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        String metinType = entry.getKey();
                        int count = entry.getValue();
                        
                        // Pobierz wyświetlaną nazwę Metina z konfiguracji, jeśli dostępna
                        String displayName = metinManager.getMetinsConfig()
                                .getString("metins." + metinType + ".display-name", metinType);
                        displayName = ChatColor.translateAlternateColorCodes('&', displayName);
                        
                        sender.sendMessage(ChatColor.YELLOW + "- " + displayName + 
                                ChatColor.GRAY + ": " + ChatColor.WHITE + count);
                    });
        }
        
        return true;
    }
    
    private boolean handleTopCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smmetin.top")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }
        
        String metinType = null;
        if (args.length >= 2 && !args[1].equalsIgnoreCase("total")) {
            metinType = args[1].toLowerCase();
            if (!metinTypes.contains(metinType)) {
                sender.sendMessage(ChatColor.RED + "Nieprawidłowy typ Metina! Dostępne typy: " + 
                        String.join(", ", metinTypes) + ", total");
                return true;
            }
        }
        
        int limit = 10; // Domyślna liczba graczy w rankingu
        if (args.length >= 3) {
            try {
                limit = Integer.parseInt(args[2]);
                limit = Math.max(1, Math.min(limit, 20)); // Ogranicz od 1 do 20
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Nieprawidłowa liczba graczy. Używam domyślnej wartości: 10");
            }
        }
        
        // Pobierz ranking
        List<Map.Entry<OfflinePlayer, Integer>> topPlayers = playerDataManager.getTopPlayers(metinType, limit);
        
        // Wyświetl ranking
        String title;
        if (metinType == null) {
            title = "Ranking graczy z największą liczbą zniszczonych Metinów:";
        } else {
            String displayName = metinManager.getMetinsConfig()
                    .getString("metins." + metinType + ".display-name", metinType);
            displayName = ChatColor.translateAlternateColorCodes('&', displayName);
            title = "Ranking graczy z największą liczbą zniszczonych Metinów typu " + displayName + ":";
        }
        
        sender.sendMessage(ChatColor.GREEN + title);
        
        if (topPlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Brak wyników do wyświetlenia.");
        } else {
            int rank = 1;
            for (Map.Entry<OfflinePlayer, Integer> entry : topPlayers) {
                OfflinePlayer player = entry.getKey();
                int count = entry.getValue();
                
                String rankPrefix;
                if (rank == 1) rankPrefix = ChatColor.GOLD + "#1 ";
                else if (rank == 2) rankPrefix = ChatColor.GRAY + "#2 ";
                else if (rank == 3) rankPrefix = ChatColor.DARK_RED + "#3 ";
                else rankPrefix = ChatColor.GRAY + "#" + rank + " ";
                
                sender.sendMessage(rankPrefix + ChatColor.YELLOW + player.getName() + 
                        ChatColor.GRAY + ": " + ChatColor.WHITE + count);
                
                rank++;
            }
        }
        
        return true;
    }

    private boolean handleRewardsCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być użyta tylko przez gracza!");
            return true;
        }
        
        if (!sender.hasPermission("smmetin.rewards")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnień do używania tej komendy!");
            return true;
        }
        
        Player player = (Player) sender;
        rewardsGUI.openMainMenu(player);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Komendy Metina ===");
        sender.sendMessage(ChatColor.YELLOW + "/metin spawn <typ>" + ChatColor.GRAY + " - Wymuszone stworzenie Metina");
        sender.sendMessage(ChatColor.YELLOW + "/metin tp <id>" + ChatColor.GRAY + " - Teleportuj do Metina");
        sender.sendMessage(ChatColor.YELLOW + "/metin list" + ChatColor.GRAY + " - Lista aktywnych Metinów");
        sender.sendMessage(ChatColor.YELLOW + "/metin remove <id>" + ChatColor.GRAY + " - Usuń konkretnego Metina");
        sender.sendMessage(ChatColor.YELLOW + "/metin removeall" + ChatColor.GRAY + " - Usuń wszystkie Metiny");
        sender.sendMessage(ChatColor.YELLOW + "/metin statystyki [gracz]" + ChatColor.GRAY + " - Wyświetl statystyki zniszczonych Metinów");
        sender.sendMessage(ChatColor.YELLOW + "/metin topka [typ] [limit]" + ChatColor.GRAY + " - Ranking graczy");
        
        if (sender.hasPermission("smmetin.rewards")) {
            sender.sendMessage(ChatColor.YELLOW + "/metin rewards" + ChatColor.GRAY + " - Konfiguracja nagród Metinów");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            
            if (sender.hasPermission("smmetin.spawn")) subcommands.add("spawn");
            if (sender.hasPermission("smmetin.teleport")) subcommands.add("tp");
            if (sender.hasPermission("smmetin.list")) subcommands.add("list");
            if (sender.hasPermission("smmetin.remove")) subcommands.add("remove");
            if (sender.hasPermission("smmetin.removeall")) subcommands.add("removeall");
            
            // Nowe komendy
            subcommands.add("statystyki");
            if (sender.hasPermission("smmetin.top")) subcommands.add("topka");
            
            return filterStartsWith(subcommands, args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawn") && sender.hasPermission("smmetin.spawn")) {
                return filterStartsWith(metinTypes, args[1]);
            } else if (args[0].equalsIgnoreCase("tp") && sender.hasPermission("smmetin.teleport")) {
                return filterStartsWith(
                        metinManager.getActiveMetins().stream()
                                .map(Metin::getId)
                                .collect(Collectors.toList()),
                        args[1]
                );
            } else if (args[0].equalsIgnoreCase("remove") && sender.hasPermission("smmetin.remove")) {
                return filterStartsWith(
                        metinManager.getActiveMetins().stream()
                                .map(Metin::getId)
                                .collect(Collectors.toList()),
                        args[1]
                );
            } else if (args[0].equalsIgnoreCase("statystyki")) {
                return filterStartsWith(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList()),
                        args[1]
                );
            } else if (args[0].equalsIgnoreCase("topka") && sender.hasPermission("smmetin.top")) {
                List<String> types = new ArrayList<>(metinTypes);
                types.add("total");
                return filterStartsWith(types, args[1]);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("topka") && sender.hasPermission("smmetin.top")) {
                return Arrays.asList("5", "10", "15", "20");
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
} 