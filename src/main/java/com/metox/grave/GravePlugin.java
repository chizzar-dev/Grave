package com.metox.grave;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Olunce esyalar bir mezarda saklanir, uzerinde hologram ve geri sayim durur.
 *
 * Mezarlar graves.yml dosyasinda saklanir; sunucu yeniden baslasa bile
 * esyalar kaybolmaz.
 */
public class GravePlugin extends JavaPlugin implements Listener, TabExecutor {

    private final Map<UUID, Grave> graves = new LinkedHashMap<UUID, Grave>();
    /** Blok konumu -> mezar kimligi (hizli arama icin). */
    private final Map<String, UUID> byBlock = new HashMap<String, UUID>();

    private File graveFile;
    private YamlConfiguration graveData;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        graveFile = new File(getDataFolder(), "graves.yml");
        graveData = YamlConfiguration.loadConfiguration(graveFile);

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("grave") != null) {
            getCommand("grave").setExecutor(this);
            getCommand("grave").setTabCompleter(this);
        }

        loadGraves();
        startTicker();

        getLogger().info("Grave aktif - algilanan surum 1." + Compat.MINOR
                + (Compat.PATCH > 0 ? "." + Compat.PATCH : "")
                + ", yuklenen mezar: " + graves.size());
    }

    @Override
    public void onDisable() {
        // Hologramlari kaldir ama mezarlari koru; yeniden acilista geri kurulurlar.
        for (Grave g : graves.values()) removeHolograms(g);
        saveGraves();
    }

    // ------------------------------------------------------------------
    // Olum
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (isDisabledWorld(p.getWorld().getName())) return;
        if (!p.hasPermission("grave.use")) return;

        List<ItemStack> drops = new ArrayList<ItemStack>();
        for (ItemStack it : e.getDrops()) {
            if (it != null && it.getType() != Material.AIR) drops.add(it.clone());
        }

        int xp = 0;
        if (getConfig().getBoolean("keep-experience", true)) {
            xp = e.getDroppedExp();
        }

        if (drops.isEmpty() && xp <= 0) return;

        int max = getConfig().getInt("max-graves-per-player", 0);
        if (max > 0) enforceLimit(p.getUniqueId(), max);

        Location loc = safeSpot(p.getLocation());
        if (loc == null) return;

        Grave g = new Grave(UUID.randomUUID(), p.getUniqueId(), p.getName(), loc);
        g.items.addAll(drops);
        g.experience = xp;
        g.expiresAt = System.currentTimeMillis()
                + Math.max(1, getConfig().getInt("duration", 60)) * 1000L;

        e.getDrops().clear();
        if (xp > 0) e.setDroppedExp(0);

        placeChest(g);
        spawnHolograms(g);

        graves.put(g.id, g);
        byBlock.put(blockKey(loc), g.id);
        saveGraves();

        String m = getConfig().getString("messages.died", "");
        if (m != null && !m.isEmpty()) {
            p.sendMessage(Compat.color(m
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%world%", loc.getWorld() == null ? "" : loc.getWorld().getName())
                    .replace("%time%", String.valueOf(g.secondsLeft()))));
        }
    }

    /** Oyuncunun en eski mezarlarini bosaltarak limiti uygular. */
    private void enforceLimit(UUID owner, int max) {
        List<Grave> mine = new ArrayList<Grave>();
        for (Grave g : graves.values()) if (g.owner.equals(owner)) mine.add(g);
        int over = mine.size() - (max - 1);
        for (int i = 0; i < over && i < mine.size(); i++) {
            expire(mine.get(i));
        }
    }

    /** Olum yerinde durulabilir bir nokta bulur (bosluga/lava icine koymaz). */
    private Location safeSpot(Location death) {
        World w = death.getWorld();
        if (w == null) return null;

        int minY = Compat.minHeight(w) + 1;
        int maxY = w.getMaxHeight() - 1;
        int x = death.getBlockX();
        int z = death.getBlockZ();
        int y = Math.max(minY, Math.min(death.getBlockY(), maxY));

        // Bosluga dustuyse yukari dogru zemin ara
        if (y <= minY) {
            for (int scan = minY; scan < maxY; scan++) {
                if (isReplaceable(w.getBlockAt(x, scan, z))
                        && !isReplaceable(w.getBlockAt(x, scan - 1, z))) {
                    y = scan;
                    break;
                }
            }
        }
        // Blogun icindeyse yukari cik
        for (int i = 0; i < 6 && y < maxY; i++) {
            if (isReplaceable(w.getBlockAt(x, y, z))) break;
            y++;
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private boolean isReplaceable(Block b) {
        if (b == null) return false;
        String n = b.getType().name();
        return n.equals("AIR") || n.equals("CAVE_AIR") || n.equals("VOID_AIR")
                || n.equals("WATER") || n.equals("STATIONARY_WATER")
                || n.contains("GRASS") && n.contains("SHORT") || n.equals("SNOW")
                || n.equals("LONG_GRASS") || n.equals("TALL_GRASS") || n.equals("SEAGRASS");
    }

    private void placeChest(Grave g) {
        if (!getConfig().getBoolean("place-chest", true)) return;
        Block b = g.location.getBlock();
        if (!isReplaceable(b)) return;
        Material chest = Compat.material(getConfig().getString("chest-material", "CHEST"), "CHEST");
        if (chest == null) return;
        try {
            b.setType(chest);
            g.chestPlaced = true;
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Hologram
    // ------------------------------------------------------------------

    private void spawnHolograms(Grave g) {
        if (!getConfig().getBoolean("hologram.enabled", true)) return;
        List<String> lines = getConfig().getStringList("hologram.lines");
        if (lines.isEmpty()) return;

        World w = g.location.getWorld();
        if (w == null) return;

        double base = g.location.getY() + getConfig().getDouble("hologram.height", 1.2);
        double gap = getConfig().getDouble("hologram.line-spacing", 0.28);

        // Ust satir en yukarida olacak sekilde yerlestir
        for (int i = 0; i < lines.size(); i++) {
            double y = base + (lines.size() - 1 - i) * gap;
            Location at = new Location(w, g.location.getX(), y, g.location.getZ());
            try {
                ArmorStand st = w.spawn(at, ArmorStand.class);
                Compat.prepHologramStand(st);
                st.setCustomName(render(lines.get(i), g));
                st.setCustomNameVisible(true);
                g.holograms.add(st.getUniqueId());
            } catch (Throwable t) {
                getLogger().warning("Hologram olusturulamadi: " + t.getMessage());
                return;
            }
        }
    }

    private void updateHolograms(Grave g) {
        if (g.holograms.isEmpty()) return;
        List<String> lines = getConfig().getStringList("hologram.lines");
        for (int i = 0; i < g.holograms.size() && i < lines.size(); i++) {
            Entity ent = findEntity(g.location.getWorld(), g.holograms.get(i));
            if (ent != null) {
                try {
                    ent.setCustomName(render(lines.get(i), g));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void removeHolograms(Grave g) {
        for (UUID id : g.holograms) {
            Entity ent = findEntity(g.location.getWorld(), id);
            if (ent != null) {
                try {
                    ent.remove();
                } catch (Throwable ignored) {
                }
            }
        }
        g.holograms.clear();
    }

    /** Bukkit.getEntity 1.12+ ile geldi; eskide dunyayi tararız. */
    private Entity findEntity(World w, UUID id) {
        try {
            Object e = Bukkit.class.getMethod("getEntity", UUID.class).invoke(null, id);
            if (e instanceof Entity) return (Entity) e;
        } catch (Throwable ignored) {
        }
        if (w == null) return null;
        try {
            for (Entity e : w.getEntities()) {
                if (e.getUniqueId().equals(id)) return e;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String render(String line, Grave g) {
        return Compat.color(line
                .replace("%player_in%", TR.genitive(g.ownerName))
                .replace("%player%", g.ownerName)
                .replace("%time%", formatTime(g.secondsLeft()))
                .replace("%items%", String.valueOf(countItems(g)))
                .replace("%xp%", String.valueOf(g.experience)));
    }

    private int countItems(Grave g) {
        int n = 0;
        for (ItemStack it : g.items) if (it != null && it.getType() != Material.AIR) n++;
        return n;
    }

    private String formatTime(long seconds) {
        if (seconds >= 60) {
            long m = seconds / 60, s = seconds % 60;
            return m + "d " + (s < 10 ? "0" : "") + s + "s";
        }
        return seconds + "s";
    }

    // ------------------------------------------------------------------
    // Zamanlayici
    // ------------------------------------------------------------------

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (graves.isEmpty()) return;
                for (Grave g : new ArrayList<Grave>(graves.values())) {
                    if (g.secondsLeft() <= 0) {
                        expire(g);
                    } else {
                        updateHolograms(g);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    /** Suresi dolan mezar: esyalar ya sahibine iade edilir ya da yere dokulur. */
    private void expire(Grave g) {
        if (!g.emptied) {
            boolean returned = false;
            if (getConfig().getBoolean("return-to-owner-on-expire", false)) {
                Player owner = Bukkit.getPlayer(g.owner);
                if (owner != null && owner.isOnline()) {
                    for (ItemStack it : g.items) giveOrDrop(owner, it);
                    if (g.experience > 0) owner.giveExp(g.experience);
                    String m = getConfig().getString("messages.returned", "");
                    if (m != null && !m.isEmpty()) owner.sendMessage(Compat.color(m));
                    returned = true;
                }
            }
            if (!returned) dropAll(g);
        }
        cleanup(g);
    }

    private void dropAll(Grave g) {
        World w = g.location.getWorld();
        if (w == null) return;
        for (ItemStack it : g.items) {
            if (it != null && it.getType() != Material.AIR) {
                try {
                    w.dropItemNaturally(g.location, it);
                } catch (Throwable ignored) {
                }
            }
        }
        if (g.experience > 0) {
            try {
                org.bukkit.entity.ExperienceOrb orb =
                        w.spawn(g.location, org.bukkit.entity.ExperienceOrb.class);
                orb.setExperience(g.experience);
            } catch (Throwable ignored) {
            }
        }
        g.items.clear();
        g.experience = 0;
    }

    /** Mezari tamamen kaldirir: sandik, hologram ve kayit. */
    private void cleanup(Grave g) {
        removeHolograms(g);
        if (g.chestPlaced) {
            Block b = g.location.getBlock();
            Material chest = Compat.material(getConfig().getString("chest-material", "CHEST"), "CHEST");
            if (chest != null && b.getType() == chest) {
                try {
                    b.setType(Material.AIR);
                } catch (Throwable ignored) {
                }
            }
        }
        graves.remove(g.id);
        byBlock.remove(blockKey(g.location));
        saveGraves();
    }

    // ------------------------------------------------------------------
    // Mezari acma
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Grave g = graveAt(e.getClickedBlock().getLocation());
        if (g == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();

        if (!canOpen(p, g)) {
            sendMsg(p, "not-owner");
            Compat.sound(p, "no", 0.8f, 1.0f);
            return;
        }
        open(p, g);
    }

    private boolean canOpen(Player p, Grave g) {
        if (p.getUniqueId().equals(g.owner)) return true;
        if (p.hasPermission("grave.open.others")) return true;
        return !getConfig().getBoolean("only-owner", false);
    }

    private void open(Player p, Grave g) {
        int size = Math.max(9, Math.min(54, ((countItems(g) + 8) / 9) * 9));
        if (size < 9) size = 9;

        GraveHolder holder = new GraveHolder(g);
        Inventory inv = Bukkit.createInventory(holder, size, Compat.title32(
                getConfig().getString("gui-title", "&8%player_in% Mezari")
                        .replace("%player_in%", TR.genitive(g.ownerName))
                        .replace("%player%", g.ownerName)));
        holder.setInventory(inv);

        int i = 0;
        for (ItemStack it : g.items) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (i >= size) break;
            inv.setItem(i++, it);
        }

        p.openInventory(inv);
        Compat.sound(p, "chest", 0.7f, 1.0f);

        if (g.experience > 0) {
            p.giveExp(g.experience);
            g.experience = 0;
            String m = getConfig().getString("messages.xp-restored", "");
            if (m != null && !m.isEmpty()) p.sendMessage(Compat.color(m));
        }
        sendMsg(p, "opened");
    }

    /** Envanter kapaninca icinde kalanlar mezara geri yazilir. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (inv == null || !(inv.getHolder() instanceof GraveHolder)) return;
        Grave g = ((GraveHolder) inv.getHolder()).getGrave();

        g.items.clear();
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) g.items.add(it);
        }

        if (g.isEmpty()) {
            g.emptied = true;
            cleanup(g);
        } else {
            saveGraves();
        }
    }

    // ------------------------------------------------------------------
    // Koruma
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Grave g = graveAt(e.getBlock().getLocation());
        if (g == null) return;
        if (!getConfig().getBoolean("protect", true)) return;

        Player p = e.getPlayer();
        if (p.getUniqueId().equals(g.owner) || p.hasPermission("grave.open.others")) {
            // Sahibi kirarsa mezari ac, blogu kirma
            e.setCancelled(true);
            open(p, g);
            return;
        }
        e.setCancelled(true);
        sendMsg(p, "not-owner");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!getConfig().getBoolean("protect", true)) return;
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            if (graveAt(it.next().getLocation()) != null) it.remove();
        }
    }

    /** Hologram tasiyicilari hicbir sekilde zarar gormesin. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof ArmorStand)) return;
        UUID id = e.getEntity().getUniqueId();
        for (Grave g : graves.values()) {
            if (g.holograms.contains(id)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private Grave graveAt(Location loc) {
        UUID id = byBlock.get(blockKey(loc));
        return id == null ? null : graves.get(id);
    }

    private String blockKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "?";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // ------------------------------------------------------------------
    // Kalicilik
    // ------------------------------------------------------------------

    private void loadGraves() {
        ConfigurationSection root = graveData.getConfigurationSection("graves");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                World w = Bukkit.getWorld(s.getString("world", ""));
                if (w == null) continue;

                Location loc = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                Grave g = new Grave(UUID.fromString(key),
                        UUID.fromString(s.getString("owner")),
                        s.getString("owner-name", "?"), loc);
                g.expiresAt = s.getLong("expires", System.currentTimeMillis());
                g.experience = s.getInt("xp", 0);
                g.chestPlaced = s.getBoolean("chest", false);

                List<?> raw = s.getList("items");
                if (raw != null) {
                    for (Object o : raw) if (o instanceof ItemStack) g.items.add((ItemStack) o);
                }

                // Eski hologramlar sunucu kapanirken silindi, yenilerini kur
                graves.put(g.id, g);
                byBlock.put(blockKey(loc), g.id);
                spawnHolograms(g);
            } catch (Throwable t) {
                getLogger().warning("Mezar okunamadi (" + key + "): " + t.getMessage());
            }
        }
    }

    private void saveGraves() {
        graveData.set("graves", null);
        for (Grave g : graves.values()) {
            String path = "graves." + g.id;
            graveData.set(path + ".owner", g.owner.toString());
            graveData.set(path + ".owner-name", g.ownerName);
            graveData.set(path + ".world", g.location.getWorld() == null
                    ? "" : g.location.getWorld().getName());
            graveData.set(path + ".x", g.location.getX());
            graveData.set(path + ".y", g.location.getY());
            graveData.set(path + ".z", g.location.getZ());
            graveData.set(path + ".expires", g.expiresAt);
            graveData.set(path + ".xp", g.experience);
            graveData.set(path + ".chest", g.chestPlaced);
            graveData.set(path + ".items", new ArrayList<ItemStack>(g.items));
        }
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            graveData.save(graveFile);
        } catch (IOException ex) {
            getLogger().warning("graves.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Komut
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ENGLISH) : "";

        if (sub.equals("reload")) {
            if (!sender.hasPermission("grave.reload")) {
                sendMsg(sender, "no-permission");
                return true;
            }
            reloadConfig();
            sendMsg(sender, "reloaded");
            return true;
        }

        if (sub.equals("list") || sub.equals("liste")) {
            if (!(sender instanceof Player)) {
                sendMsg(sender, "players-only");
                return true;
            }
            Player p = (Player) sender;
            List<Grave> mine = new ArrayList<Grave>();
            for (Grave g : graves.values()) if (g.owner.equals(p.getUniqueId())) mine.add(g);
            if (mine.isEmpty()) {
                sendMsg(p, "no-graves");
                return true;
            }
            for (Grave g : mine) {
                p.sendMessage(Compat.color(getConfig()
                        .getString("messages.list-entry", "&7- &f%world% &7(%x%, %y%, %z%) &8- &e%time%")
                        .replace("%world%", g.location.getWorld() == null ? "?" : g.location.getWorld().getName())
                        .replace("%x%", String.valueOf(g.location.getBlockX()))
                        .replace("%y%", String.valueOf(g.location.getBlockY()))
                        .replace("%z%", String.valueOf(g.location.getBlockZ()))
                        .replace("%time%", formatTime(g.secondsLeft()))));
            }
            return true;
        }

        sender.sendMessage(Compat.color("&7/" + label + " list &8| &7/" + label + " reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<String>();
            for (String s : Arrays.asList("list", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ENGLISH))) out.add(s);
            }
            return out;
        }
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private void giveOrDrop(Player p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rem : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rem);
        }
    }

    private boolean isDisabledWorld(String world) {
        for (String w : getConfig().getStringList("disabled-worlds")) {
            if (w != null && w.equalsIgnoreCase(world)) return true;
        }
        return false;
    }

    private void sendMsg(CommandSender s, String key) {
        String m = getConfig().getString("messages." + key, "");
        if (m != null && !m.isEmpty()) s.sendMessage(Compat.color(m));
    }
}
