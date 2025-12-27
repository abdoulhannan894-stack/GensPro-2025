package com.tuservidor.gens;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GensPro extends JavaPlugin implements Listener, CommandExecutor {

    private Connection connection;
    private final ConcurrentHashMap<UUID, Location> playerIslands = new ConcurrentHashMap<>();
    private final String SCHEM_NAME = "isla_maestra.schematic";

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();

        setupDatabase();
        loadAllData();

        getCommand("gens").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() { saveAllData(); }
        }.runTaskTimerAsynchronously(this, 100L, 100L);
    }

    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/data.db");
            try (Statement s = connection.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS islands (uuid VARCHAR(36) PRIMARY KEY, x DOUBLE, y DOUBLE, z DOUBLE, world TEXT)");
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error DB", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length >= 2 && args[0].equalsIgnoreCase("is") && args[1].equalsIgnoreCase("create")) {
            if (playerIslands.containsKey(player.getUniqueId())) {
                player.sendMessage("§cYa tienes isla.");
            } else { createIsland(player); }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("is")) {
            Location loc = playerIslands.get(player.getUniqueId());
            if (loc != null) { player.teleport(loc); }
            else { player.sendMessage("§eUsa: /gens is create"); }
            return true;
        }
        return false;
    }

    private void createIsland(Player player) {
        File schemFile = new File(getDataFolder(), "schematics/" + SCHEM_NAME);
        if (!schemFile.exists()) {
            player.sendMessage("§cNo se encontro el archivo .schematic");
            return;
        }
        Location spawnLoc = new Location(player.getWorld(), (playerIslands.size() + 1) * 1000.0, 100.0, 0);
        StructureManager sm = Bukkit.getStructureManager();
        try {
            Structure structure = sm.loadStructure(schemFile);
            structure.place(spawnLoc, true, org.bukkit.block.structure.Mirror.NONE, 
                            org.bukkit.block.structure.StructureRotation.NONE, 0, 1, new java.util.Random());
            Location safeLoc = spawnLoc.add(structure.getSize().getX() / 2.0, 1.5, structure.getSize().getZ() / 2.0);
            playerIslands.put(player.getUniqueId(), safeLoc);
            player.teleport(safeLoc);
        } catch (Exception e) { player.sendMessage("§cError cargando estructura."); }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVoidFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            Location home = playerIslands.get(player.getUniqueId());
            if (home != null) {
                event.setCancelled(true);
                player.setFallDistance(0);
                player.teleport(home);
            }
        }
    }

    private synchronized void saveAllData() {
        try (PreparedStatement ps = connection.prepareStatement("REPLACE INTO islands VALUES (?, ?, ?, ?, ?)")) {
            for (java.util.Map.Entry<UUID, Location> entry : playerIslands.entrySet()) {
                Location l = entry.getValue();
                ps.setString(1, entry.getKey().toString());
                ps.setDouble(2, l.getX());
                ps.setDouble(3, l.getY());
                ps.setDouble(4, l.getZ());
                ps.setString(5, l.getWorld().getName());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadAllData() {
        try (ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM islands")) {
            while (rs.next()) {
                playerIslands.put(UUID.fromString(rs.getString("uuid")), 
                    new Location(Bukkit.getWorld(rs.getString("world")), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }
    }

