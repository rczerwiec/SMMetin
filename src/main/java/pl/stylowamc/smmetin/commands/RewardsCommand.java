package pl.stylowamc.smmetin.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.stylowamc.smmetin.Smmetin;
import pl.stylowamc.smmetin.gui.RewardsGUI;

public class RewardsCommand implements CommandExecutor {
    
    private final Smmetin plugin;
    private final RewardsGUI rewardsGUI;
    
    public RewardsCommand(Smmetin plugin) {
        this.plugin = plugin;
        this.rewardsGUI = new RewardsGUI(plugin);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ta komenda może być użyta tylko przez gracza!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("smmetin.rewards")) {
            player.sendMessage(ChatColor.RED + "Nie masz uprawnień do zarządzania nagrodami Metinów!");
            return true;
        }
        
        // Otwórz menu główne nagród
        rewardsGUI.openMainMenu(player);
        player.sendMessage(ChatColor.GREEN + "Otwarto menu konfiguracji nagród dla Metinów.");
        
        return true;
    }
} 