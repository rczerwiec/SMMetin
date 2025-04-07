package pl.stylowamc.smmetin.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import pl.stylowamc.smmetin.Smmetin;
import pl.stylowamc.smmetin.lib.VaultEconomyInterface;

public class EconomyManager {
    private final Smmetin plugin;
    private final boolean debug;
    private VaultEconomyInterface economy;
    private boolean vaultEnabled = false;

    public EconomyManager(Smmetin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("settings.debug", true);
        
        // Inicjalizuj połączenie z Vault
        setupEconomy();
    }
    
    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [Economy] " + message);
        }
    }
    
    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            if (debug) {
                debugLog("Nie znaleziono pluginu Vault! Ekonomia będzie wyłączona.");
            }
            return false;
        }
        
        try {
            // Próba wczytania Economy z Vault
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            
            if (rsp == null) {
                if (debug) {
                    debugLog("Nie znaleziono zarejestrowanej usługi ekonomii! Ekonomia będzie wyłączona.");
                }
                return false;
            }
            
            // Użyj adaptacji opartej na proxy, aby uniknąć bezpośredniego odwoływania się do klas Vault
            Object originalEconomy = rsp.getProvider();
            economy = new VaultEconomyAdapter(originalEconomy);
            vaultEnabled = true;
            
            if (debug) {
                debugLog("Pomyślnie połączono z Vault. Ekonomia jest włączona.");
                debugLog("Provider: " + economy.getName());
            }
            
            return true;
        } catch (ClassNotFoundException e) {
            if (debug) {
                debugLog("Nie znaleziono klasy Economy z Vault: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Sprawdza, czy system ekonomii jest aktywny
     * @return true jeśli ekonomia jest aktywna, false w przeciwnym razie
     */
    public boolean isEnabled() {
        return vaultEnabled && economy != null;
    }
    
    /**
     * Dodaje określoną kwotę pieniędzy do gracza
     * @param player Gracz, który ma otrzymać pieniądze
     * @param amount Kwota do dodania
     * @return true jeśli operacja się powiodła, false w przeciwnym razie
     */
    public boolean addMoney(Player player, double amount) {
        if (!vaultEnabled || economy == null) {
            if (debug) {
                debugLog("Próba dodania pieniędzy, ale ekonomia jest wyłączona!");
            }
            return false;
        }
        
        if (player == null) {
            if (debug) {
                debugLog("Próba dodania pieniędzy do null gracza!");
            }
            return false;
        }
        
        try {
            economy.depositPlayer(player, amount);
            
            if (debug) {
                debugLog("Dodano " + amount + " pieniędzy do gracza " + player.getName());
            }
            
            return true;
        } catch (Exception e) {
            if (debug) {
                debugLog("Błąd podczas dodawania pieniędzy do gracza " + player.getName() + ": " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Pobiera nazwę waluty
     * @param amount Kwota (dla określenia czy pojedyncza czy mnoga forma)
     * @return Nazwa waluty
     */
    public String getCurrencyName(double amount) {
        if (!vaultEnabled || economy == null) {
            return "pieniędzy";
        }
        
        return amount == 1 ? economy.currencyNameSingular() : economy.currencyNamePlural();
    }
    
    /**
     * Adapter dla Economy z Vault, który implementuje nasz interfejs
     */
    private class VaultEconomyAdapter implements VaultEconomyInterface {
        private final Object originalEconomy;
        
        public VaultEconomyAdapter(Object originalEconomy) {
            this.originalEconomy = originalEconomy;
        }
        
        @Override
        public boolean isEnabled() {
            try {
                return (boolean) originalEconomy.getClass().getMethod("isEnabled").invoke(originalEconomy);
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd podczas sprawdzania czy ekonomia jest włączona: " + e.getMessage());
                }
                return false;
            }
        }
        
        @Override
        public boolean depositPlayer(org.bukkit.OfflinePlayer player, double amount) {
            try {
                return (boolean) originalEconomy.getClass()
                    .getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class)
                    .invoke(originalEconomy, player, amount);
            } catch (Exception e) {
                if (debug) {
                    debugLog("Błąd podczas dodawania pieniędzy: " + e.getMessage());
                }
                return false;
            }
        }
        
        @Override
        public String currencyNameSingular() {
            try {
                return (String) originalEconomy.getClass().getMethod("currencyNameSingular").invoke(originalEconomy);
            } catch (Exception e) {
                return "pieniądz";
            }
        }
        
        @Override
        public String currencyNamePlural() {
            try {
                return (String) originalEconomy.getClass().getMethod("currencyNamePlural").invoke(originalEconomy);
            } catch (Exception e) {
                return "pieniędzy";
            }
        }
        
        @Override
        public String getName() {
            try {
                return (String) originalEconomy.getClass().getMethod("getName").invoke(originalEconomy);
            } catch (Exception e) {
                return "Nieznana";
            }
        }
    }
} 