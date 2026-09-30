package com.metox.grave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Acilan mezar envanterini mezara baglar. */
public class GraveHolder implements InventoryHolder {

    private final Grave grave;
    private Inventory inventory;

    public GraveHolder(Grave grave) {
        this.grave = grave;
    }

    public Grave getGrave() {
        return grave;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
