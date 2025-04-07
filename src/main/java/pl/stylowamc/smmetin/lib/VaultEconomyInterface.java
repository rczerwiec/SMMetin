package pl.stylowamc.smmetin.lib;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Uproszczony interfejs ekonomii Vault do użytku wewnątrz pluginu
 * Ten interfejs pozwala uniknąć błędów linterowych związanych z uzależnieniem od Vault
 */
public interface VaultEconomyInterface {
    
    /**
     * Sprawdza czy ekonomia jest włączona
     * @return true jeśli ekonomia jest włączona
     */
    boolean isEnabled();
    
    /**
     * Dodaje określoną kwotę pieniędzy do konta gracza
     * @param player Gracz, który ma otrzymać pieniądze
     * @param amount Kwota do dodania
     * @return true jeśli operacja się powiodła
     */
    boolean depositPlayer(OfflinePlayer player, double amount);
    
    /**
     * Zwraca nazwę waluty w liczbie pojedynczej
     * @return Nazwa waluty w liczbie pojedynczej
     */
    String currencyNameSingular();
    
    /**
     * Zwraca nazwę waluty w liczbie mnogiej
     * @return Nazwa waluty w liczbie mnogiej
     */
    String currencyNamePlural();
    
    /**
     * Zwraca nazwę pluginu ekonomii
     * @return Nazwa pluginu ekonomii
     */
    String getName();
} 