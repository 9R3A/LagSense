package com.niner3a.lagsense;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LagSense extends JavaPlugin {

    private final Map<UUID, Long> lastChunkLoad = new HashMap<>();
    private final Map<UUID, Double> tpsHistory = new HashMap<>();
    private final DecimalFormat df = new DecimalFormat("0.00");
    private static final double WARNING_THRESHOLD = 0.9; // 90% of system resources used

    private static final String[] LOGO = new String[]{
        "&6╦  ╦┌─┐┌┐┌┌─┐╔═╗┌─┐┌┐┌┌─┐┌─┐",
        "&6╚╗╔╝├┤ ││││  ║  │ ││││├┤ ├┤ ",
        "&6 ╚╝ └─┘┘└┘└─┘╚═╝└─┘┘└┘└─┘└  ",
        "&7  &fPersonal Lag Analyzer &7v%s &8[&a✓&8]"
    };
    
    private String pluginVersion;

    @Override
    public void onEnable() {
        // Save version for later use
        PluginDescriptionFile pdf = getDescription();
        pluginVersion = pdf.getVersion();
        
        // Print fancy logo and info
        printLogo();
        
        getLogger().info("");
        sendColoredMessage("&7Version: &f" + pluginVersion);
        sendColoredMessage("&7Author: &f" + pdf.getAuthors().get(0));
        sendColoredMessage("&7Website: &f" + pdf.getWebsite());
        sendColoredMessage("&7Use &a/lagsense &7to analyze your lag!");
        getLogger().info("");
        
        // Register metrics if needed (you can add bStats later)
        // https://bstats.org/plugin/bukkit/LagSense
        // Metrics metrics = new Metrics(this, YOUR_PLUGIN_ID);
        
        getLogger().info("LagSense has been enabled!");
        
        // Schedule repeating task to monitor TPS and other metrics
        new BukkitRunnable() {
            @Override
            public void run() {
                double tps = getTps();
                double cpuLoad = getCpuLoad();
                double memoryUsage = getMemoryUsage();
                
                // Store TPS for player analysis
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tpsHistory.put(player.getUniqueId(), tps);
                }
                
                // Check for system-wide issues
                if (cpuLoad > WARNING_THRESHOLD || memoryUsage > WARNING_THRESHOLD) {
                    getLogger().warning("High system resource usage detected! CPU: " + 
                            (int)(cpuLoad * 100) + "%, Memory: " + (int)(memoryUsage * 100) + "%");
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Run every second
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        
        if (cmd.getName().equalsIgnoreCase("lagsense") || 
            cmd.getName().equalsIgnoreCase("ls") || 
            cmd.getName().equalsIgnoreCase("lag")) {
            
            analyzeAndSendReport(player);
            return true;
        }
        
        return false;
    }
    
    private void analyzeAndSendReport(Player player) {
        long ping = getPing(player);
        double tps = tpsHistory.getOrDefault(player.getUniqueId(), 20.0);
        double cpuLoad = getCpuLoad();
        double memoryUsage = getMemoryUsage();
        
        // Calculate chunk load time if available
        Long lastLoad = lastChunkLoad.get(player.getUniqueId());
        long chunkLoadTime = lastLoad != null ? System.currentTimeMillis() - lastLoad : -1;
        
        // Build the report
        StringBuilder report = new StringBuilder();
        report.append(ChatColor.GOLD).append("\n=== ")
              .append(ChatColor.YELLOW).append("LagSense Report")
              .append(ChatColor.GOLD).append(" ===\n");
        
        // Basic metrics
        report.append(ChatColor.WHITE).append("Ping: ").append(formatPing(ping)).append("\n");
        report.append(ChatColor.WHITE).append("Server TPS: ").append(formatTps(tps)).append("\n");
        
        if (chunkLoadTime > 0) {
            report.append(ChatColor.WHITE).append("Chunk Load Time: ").append(chunkLoadTime).append("ms\n");
        }
        
        // System metrics
        report.append(ChatColor.WHITE).append("CPU Usage: ").append(formatPercentage(cpuLoad)).append("\n");
        report.append(ChatColor.WHITE).append("Memory Usage: ").append(formatPercentage(memoryUsage)).append("\n\n");
        
        // Analysis and suggestions
        report.append(ChatColor.GOLD).append("Analysis:\n");
        
        boolean hasIssues = false;
        
        // Ping analysis
        if (ping > 300) {
            report.append(ChatColor.RED).append("• Your ping is very high! Consider connecting to a closer server or checking your internet connection.\n");
            hasIssues = true;
        } else if (ping > 150) {
            report.append(ChatColor.YELLOW).append("• Your ping is slightly high. Try using a wired connection if possible.\n");
            hasIssues = true;
        }
        
        // TPS analysis
        if (tps < 15) {
            report.append(ChatColor.RED).append("• Server is experiencing lag. This is a server-side issue.\n");
            hasIssues = true;
        } else if (tps < 19) {
            report.append(ChatColor.YELLOW).append("• Server performance is slightly degraded.\n");
            hasIssues = true;
        }
        
        // Chunk load analysis
        if (chunkLoadTime > 500) {
            report.append(ChatColor.YELLOW).append("• Slow chunk loading detected. Try lowering your render distance.\n");
            hasIssues = true;
        }
        
        // System resource analysis
        if (cpuLoad > WARNING_THRESHOLD) {
            report.append(ChatColor.RED).append("• High CPU usage detected on the server.\n");
            hasIssues = true;
        }
        
        if (memoryUsage > WARNING_THRESHOLD) {
            report.append(ChatColor.RED).append("• High memory usage detected on the server.\n");
            hasIssues = true;
        }
        
        if (!hasIssues) {
            report.append(ChatColor.GREEN).append("• No significant lag issues detected!\n");
        }
        
        // General tips
        report.append("\n").append(ChatColor.GOLD).append("Tips:\n");
        report.append(ChatColor.WHITE).append("• Use ").append(ChatColor.YELLOW).append("/lagsense").append(ChatColor.WHITE).append(" to check your lag status\n");
        report.append("• Lower render distance if experiencing chunk loading issues\n");
        report.append("• Close other applications to free up system resources\n");
        
        player.sendMessage(report.toString());
    }
    
    private long getPing(Player player) {
        try {
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            return (int) entityPlayer.getClass().getField("ping").get(entityPlayer);
        } catch (Exception e) {
            return -1;
        }
    }
    
    private double getTps() {
        try {
            Object server = Bukkit.getServer();
            Object minecraftServer = server.getClass().getMethod("getServer").invoke(server);
            
            // Get recent TPS (1m average)
            double[] tps = (double[]) minecraftServer.getClass()
                .getMethod("recentTps")
                .invoke(minecraftServer);
                
            return Math.min(20.0, tps[0]); // Return 1m average, capped at 20.0
        } catch (Exception e) {
            return 20.0; // Default to perfect TPS if we can't measure
        }
    }
    
    private double getCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                (com.sun.management.OperatingSystemMXBean) osBean;
            return sunOsBean.getCpuLoad();
        }
        return -1.0; // Not available
    }
    
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        return (double) usedMemory / maxMemory;
    }
    
    private String formatPing(long ping) {
        if (ping < 0) return "N/A";
        String pingStr = String.valueOf(ping);
        if (ping < 100) return ChatColor.GREEN + pingStr + "ms";
        if (ping < 200) return ChatColor.YELLOW + pingStr + "ms";
        return ChatColor.RED + pingStr + "ms";
    }
    
    private String formatTps(double tps) {
        String tpsStr = df.format(tps);
        if (tps > 18.0) return ChatColor.GREEN + tpsStr;
        if (tps > 15.0) return ChatColor.YELLOW + tpsStr;
        return ChatColor.RED + tpsStr;
    }
    
    private String formatPercentage(double value) {
        if (value < 0) return "N/A";
        double percentage = value * 100;
        String percentageStr = df.format(percentage);
        if (percentage < 70) return ChatColor.GREEN + percentageStr + "%";
        if (percentage < 90) return ChatColor.YELLOW + percentageStr + "%";
        return ChatColor.RED + percentageStr + "%";
    }
    
    private void printLogo() {
        for (String line : LOGO) {
            String formatted = ChatColor.translateAlternateColorCodes('&', 
                String.format(line, pluginVersion));
            getServer().getConsoleSender().sendMessage(formatted);
        }
    }
    
    private void sendColoredMessage(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        getServer().getConsoleSender().sendMessage(formatted);
        getLogger().info(ChatColor.stripColor(formatted));
    }
    
    @Override
    public void onDisable() {
        getLogger().info("");
        sendColoredMessage("&6LagSense &7has been &cdisabled");
        getLogger().info("");
    }
}
