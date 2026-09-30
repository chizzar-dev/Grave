package com.metox.grave;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tek bir mezarin durumu. */
public class Grave {

    public final UUID id;
    public final UUID owner;
    public final String ownerName;
    public final Location location;

    /** Icindeki esyalar (bos yuvalar null olabilir). */
    public final List<ItemStack> items = new ArrayList<ItemStack>();
    /** Mezarla birlikte saklanan tecrube. */
    public int experience;
    /** Mezarin sona erecegi zaman (epoch ms). */
    public long expiresAt;

    /** Sandik konuldu mu (konulmadiysa kaldirirken blogu silmemeliyiz). */
    public boolean chestPlaced;
    /** Hologram tasiyicilarinin kimlikleri. */
    public final List<UUID> holograms = new ArrayList<UUID>();
    /** Esyalar alindi/dagitildi mi. */
    public boolean emptied;

    public Grave(UUID id, UUID owner, String ownerName, Location location) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.location = location;
    }

    public long secondsLeft() {
        long ms = expiresAt - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public boolean isEmpty() {
        for (ItemStack it : items) {
            if (it != null && it.getType() != org.bukkit.Material.AIR) return false;
        }
        return experience <= 0;
    }
}
