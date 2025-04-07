package pl.stylowamc.smmetin.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import pl.stylowamc.smmetin.Smmetin;
import pl.stylowamc.smmetin.manager.MetinManager;
import pl.stylowamc.smmetin.metin.Metin;

public class MetinListener implements Listener {
    private final Smmetin plugin;
    private final MetinManager metinManager;
    private final YamlConfiguration config;
    private final boolean debug;

    public MetinListener(Smmetin plugin) {
        this.plugin = plugin;
        this.metinManager = plugin.getMetinManager();
        this.config = metinManager.getMetinsConfig();
        this.debug = config.getRoot().getConfigurationSection("settings").getBoolean("debug", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || clickedBlock == null) {
            return;
        }

        if (debug) {
            debugLog("Otrzymano zdarzenie PlayerInteractEvent dla bloku: " + clickedBlock.getType());
        }

        Metin metin = metinManager.getMetinAtLocation(clickedBlock.getLocation());
        if (metin == null) {
            return;
        }

        if (debug) {
            debugLog("Znaleziono Metina o ID: " + metin.getId());
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        event.setCancelled(true);
        metin.damage(player, 1.0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (debug) {
            debugLog("Otrzymano zdarzenie BlockBreakEvent dla bloku: " + event.getBlock().getType());
        }

        Metin metin = metinManager.getMetinAtLocation(event.getBlock().getLocation());
        if (metin != null) {
            event.setCancelled(true);
            if (debug) {
                debugLog("Anulowano zniszczenie bloku Metina o ID: " + metin.getId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (debug) {
            debugLog("Otrzymano zdarzenie BlockExplodeEvent");
        }

        event.blockList().removeIf(block -> {
            Metin metin = metinManager.getMetinAtLocation(block.getLocation());
            if (metin != null) {
                if (debug) {
                    debugLog("Usunięto blok Metina o ID: " + metin.getId() + " z listy bloków do wybuchu");
                }
                return true;
            }
            return false;
        });
    }

    private void debugLog(String message) {
        if (debug) {
            System.out.println("[SMMetin Debug] [MetinListener] " + message);
        }
    }
} 