package pl.stylowamc.smmetin.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pl.stylowamc.smmetin.Smmetin;
import org.bukkit.ChatColor;

import java.util.List;
import java.util.Map;

/**
 * Klasa obsługująca placeholdery dla pluginu SMMetin
 */
public class SMMetinExpansion extends PlaceholderExpansion {

    private final Smmetin plugin;

    /**
     * Konstruktor klasy
     * @param plugin Instancja pluginu SMMetin
     */
    public SMMetinExpansion(Smmetin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "smmetin";
    }

    @Override
    public String getAuthor() {
        return "Styluś";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        // Jeśli nie podano parametrów, zwróć null
        if (params == null || params.isEmpty()) {
            return null;
        }

        // Obsługa placeholdera %smmetin_total%
        if (params.equals("total")) {
            if (player == null) {
                return "&c0";
            }
            int total = plugin.getPlayerDataManager().getTotalDestroyed(player.getUniqueId());
            return formatNumber(total);
        }

        // Obsługa placeholdera %smmetin_total_topX%
        if (params.startsWith("total_top")) {
            try {
                int position = Integer.parseInt(params.substring(9));
                if (position < 1 || position > 10) {
                    return "&cNieprawidłowa pozycja";
                }
                
                List<Map.Entry<OfflinePlayer, Integer>> topPlayers = 
                    plugin.getPlayerDataManager().getTopPlayers(null, 10);
                
                if (position > topPlayers.size()) {
                    return "&7Brak danych";
                }
                
                Map.Entry<OfflinePlayer, Integer> entry = topPlayers.get(position - 1);
                String rankColor = getRankColor(position);
                String playerName = entry.getKey().getName();
                int count = entry.getValue();
                
                return String.format("%s#%d &f%s &7(&e%s&7)", 
                    rankColor, position, playerName, formatNumber(count));
            } catch (NumberFormatException e) {
                return "&cNieprawidłowa pozycja";
            }
        }

        // Obsługa placeholdera %smmetin_[typ]_topX%
        if (params.contains("_top")) {
            String[] parts = params.split("_top");
            if (parts.length != 2) {
                return null;
            }
            
            String metinType = parts[0];
            try {
                int position = Integer.parseInt(parts[1]);
                if (position < 1 || position > 10) {
                    return "&cNieprawidłowa pozycja";
                }
                
                List<Map.Entry<OfflinePlayer, Integer>> topPlayers = 
                    plugin.getPlayerDataManager().getTopPlayers(metinType, 10);
                
                if (position > topPlayers.size()) {
                    return "&7Brak danych";
                }
                
                Map.Entry<OfflinePlayer, Integer> entry = topPlayers.get(position - 1);
                String rankColor = getRankColor(position);
                String playerName = entry.getKey().getName();
                int count = entry.getValue();
                
                // Pobierz wyświetlaną nazwę Metina z konfiguracji
                String displayName = plugin.getMetinManager().getMetinsConfig()
                    .getString("metins." + metinType + ".display-name", metinType);
                displayName = ChatColor.translateAlternateColorCodes('&', displayName);
                
                return String.format("%s#%d &f%s &7(&e%s&7) &8[%s&8]", 
                    rankColor, position, playerName, formatNumber(count), displayName);
            } catch (NumberFormatException e) {
                return "&cNieprawidłowa pozycja";
            }
        }

        // Obsługa placeholdera %smmetin_[typ]%
        if (player != null) {
            int count = plugin.getPlayerDataManager().getSpecificMetinDestroyed(player.getUniqueId(), params);
            return formatNumber(count);
        }

        return null;
    }

    /**
     * Formatuje liczbę dodając separatory tysięcy
     */
    private String formatNumber(int number) {
        return String.format("&e%,d", number).replace(",", "&7,&e");
    }

    /**
     * Zwraca kolor dla danej pozycji w rankingu
     */
    private String getRankColor(int position) {
        switch (position) {
            case 1: return "&6"; // Złoty
            case 2: return "&7"; // Srebrny
            case 3: return "&c"; // Brązowy (czerwony)
            default: return "&8"; // Szary
        }
    }
} 