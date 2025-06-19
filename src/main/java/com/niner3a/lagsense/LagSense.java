package com.niner3a.lagsense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

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
    private FileConfiguration config;
    private File configFile;
    private int consecutiveLagChecks = 0;
    private long lastNotificationTime = 0;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void onEnable() {
        // Save version for later use
        PluginDescriptionFile pdf = getDescription();
        pluginVersion = pdf.getVersion();
        
        // Load or create config
        saveDefaultConfig();
        config = getConfig();
        
        // Print fancy logo and info
        printLogo();
        
        getLogger().info("");
        sendColoredMessage("&7Version: &f" + pluginVersion);
        sendColoredMessage("&7Author: &f" + pdf.getAuthors().get(0));
        sendColoredMessage("&7Website: &f" + pdf.getWebsite());
        sendColoredMessage("&7Use &a/lagsense &7to analyze your lag!");
        getLogger().info("");
        
        // Start lag detection task
        startLagDetectionTask();
        
        getLogger().info("LagSense has been enabled!");
        
        // Check if Discord webhook is enabled but URL is not set
        if (config.getBoolean("discord.enabled", false) && 
            (config.getString("discord.webhook-url", "").isEmpty())) {
            getLogger().warning("Discord webhook is enabled but no webhook URL is set!");
            getLogger().warning("Please set 'discord.webhook-url' in config.yml");
        }
        
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
    
    @Override
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }
    
    private void startLagDetectionTask() {
        long checkInterval = config.getLong("lag-detection.check-interval", 60) * 20L;
        if (checkInterval <= 0) {
            getLogger().warning("Invalid check-interval in config.yml, using default 60 seconds");
            checkInterval = 60 * 20L;
        }
        
        new BukkitRunnable() {
            private int consecutiveChecks = 0;
            
            @Override
            public void run() {
                double tps = getTps();
                double cpuLoad = getCpuLoad() * 100;
                double memoryUsage = getMemoryUsage() * 100;
                
                double tpsThreshold = config.getDouble("lag-detection.tps-threshold", 18.0);
                double cpuThreshold = config.getDouble("lag-detection.cpu-threshold", 85.0);
                double memoryThreshold = config.getDouble("lag-detection.memory-threshold", 85.0);
                int minConsecutiveChecks = config.getInt("lag-detection.min-consecutive-checks", 2);
                
                boolean isLagging = tps < tpsThreshold || 
                                  cpuLoad > cpuThreshold || 
                                  memoryUsage > memoryThreshold;
                
                if (isLagging) {
                    consecutiveChecks++;
                    if (consecutiveChecks >= minConsecutiveChecks) {
                        checkAndSendDiscordNotification(tps, cpuLoad, memoryUsage);
                    }
                } else {
                    consecutiveChecks = 0;
                }
            }
        }.runTaskTimerAsynchronously(this, 100L, checkInterval);
    }
    
    private void checkAndSendDiscordNotification(double tps, double cpuUsage, double memoryUsage) {
        if (!config.getBoolean("discord.enabled", false)) {
            return;
        }
        
        String webhookUrl = config.getString("discord.webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long cooldown = config.getLong("lag-detection.notification-cooldown", 300) * 1000L;
        
        if (currentTime - lastNotificationTime < cooldown) {
            return; // Still in cooldown period
        }
        
        lastNotificationTime = currentTime;
        
        // Format the message
        String title = config.getString("discord.message.title", "⚠️ Server Lag Detected");
        int color = config.getInt("discord.message.color", 15158332);
        String footer = config.getString("discord.message.footer", "LagSense v{version} | {time}")
                .replace("{version}", pluginVersion)
                .replace("{time}", timeFormat.format(new Date()) + " " + dateFormat.format(new Date()));
        
        // Create embed
        ObjectNode embed = objectMapper.createObjectNode();
        embed.put("title", title);
        embed.put("color", color);
        embed.put("timestamp", new Date().toInstant().toString());
        
        // Add fields
        ArrayNode fields = objectMapper.createArrayNode();
        
        ObjectNode tpsField = objectNode()
                .put("name", "TPS")
                .put("value", String.format("%.2f", tps) + " (Threshold: " + config.getDouble("lag-detection.tps-threshold", 18.0) + ")")
                .put("inline", true);
        fields.add(tpsField);
        
        ObjectNode cpuField = objectNode()
                .put("name", "CPU Usage")
                .put("value", String.format("%.1f%%", cpuUsage) + " (Threshold: " + config.getDouble("lag-detection.cpu-threshold", 85.0) + "%)")
                .put("inline", true);
        fields.add(cpuField);
        
        ObjectNode memoryField = objectNode()
                .put("name", "Memory Usage")
                .put("value", String.format("%.1f%%", memoryUsage) + " (Threshold: " + config.getDouble("lag-detection.memory-threshold", 85.0) + "%)")
                .put("inline", true);
        fields.add(memoryField);
        
        // Add server info
        ObjectNode serverField = objectNode()
                .put("name", "Server Info")
                .put("value", "**Online Players:** " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + 
                        "\n**Uptime:** " + formatUptime(ManagementFactory.getRuntimeMXBean().getUptime() / 1000))
                .put("inline", false);
        fields.add(serverField);
        
        embed.set("fields", fields);
        
        // Add footer
        ObjectNode footerNode = objectNode()
                .put("text", footer);
        embed.set("footer", footerNode);
        
        // Create webhook payload
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", config.getString("discord.username", "LagSense"));
        
        String avatarUrl = config.getString("discord.avatar-url", "");
        if (!avatarUrl.isEmpty()) {
            payload.put("avatar_url", avatarUrl);
        }
        
        ArrayNode embeds = objectMapper.createArrayNode();
        embeds.add(embed);
        payload.set("embeds", embeds);
        
        // Send webhook asynchronously
        CompletableFuture.runAsync(() -> sendWebhook(webhookUrl, payload.toString()));
    }
    
    private ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }
    
    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        
        return sb.toString().trim();
    }
    
    private void sendWebhook(String url, String jsonPayload) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "LagSense/" + pluginVersion);
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                getLogger().warning("Failed to send Discord webhook. Response code: " + responseCode);
            }
            
            connection.disconnect();
        } catch (IOException e) {
            getLogger().warning("Error sending Discord webhook: " + e.getMessage());
        }
    }
}
