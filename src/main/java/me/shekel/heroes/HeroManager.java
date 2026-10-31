package me.shekel.heroes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps track of who is which hero, their power items, passives and cooldowns. */
public class HeroManager {

    private final SolarHeroes plugin;
    public final NamespacedKey heroKey;
    public final NamespacedKey itemKey;
    public final NamespacedKey arrowKey;
    public final NamespacedKey bodyKey;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> suppressedUntil = new HashMap<>();

    public HeroManager(SolarHeroes plugin) {
        this.plugin = plugin;
        this.heroKey = new NamespacedKey(plugin, "hero");
        this.itemKey = new NamespacedKey(plugin, "power_item");
        this.arrowKey = new NamespacedKey(plugin, "portal_arrow");
        this.bodyKey = new NamespacedKey(plugin, "nas_body");
    }

    // ---------------------------------------------------------------- hero state

    public Hero getHero(Player p) {
        String s = p.getPersistentDataContainer().get(heroKey, PersistentDataType.STRING);
        return Hero.parse(s);
    }

    public void setHero(Player p, Hero hero) {
        Hero old = getHero(p);
        if (old != null) cleanup(p, old);

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (hero == null) {
            pdc.remove(heroKey);
            Fx.msg(p, "You are no longer a hero.", NamedTextColor.GRAY);
            return;
        }

        pdc.set(heroKey, PersistentDataType.STRING, hero.name());
        giveItems(p, hero);
        applyPassives(p, hero);

        p.showTitle(Title.title(
                Component.text(hero.displayName(), hero.color(), TextDecoration.BOLD),
                Component.text("Your powers are awakening...", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.05);

        Fx.msg(p, "You are now " + hero.displayName() + "!", hero.color());
        for (String line : hero.controls()) {
            Fx.msg(p, "  • " + line, NamedTextColor.GRAY);
        }
    }

    /** Removes everything the old hero gave the player. */
    public void cleanup(Player p, Hero old) {
        removeItems(p);
        removePassives(p, old);
        plugin.solar().endFlight(p);
        plugin.nas().unshift(p, false);
        plugin.phantom().clearPortals(p);
        suppressedUntil.remove(p.getUniqueId());
    }

    // ---------------------------------------------------------------- items

    public ItemStack createItem(Hero hero) {
        ItemStack item = new ItemStack(hero.itemType());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(hero.itemName(), hero.color(), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Power of " + hero.displayName(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        for (String line : hero.controls()) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        if (hero == Hero.PHANTOM) {
            meta.addEnchant(Enchantment.INFINITY, 1, true);
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, hero.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Which hero this item belongs to, or null if it's a normal item. */
    public Hero itemHero(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return Hero.parse(s);
    }

    public boolean isHeroItem(ItemStack item) {
        return itemHero(item) != null;
    }

    public void giveItems(Player p, Hero hero) {
        boolean hasItem = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (itemHero(it) == hero) { hasItem = true; break; }
        }
        if (!hasItem) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(createItem(hero));
            for (ItemStack it : left.values()) {
                p.getWorld().dropItem(p.getLocation(), it);
            }
        }
        if (hero == Hero.PHANTOM && !p.getInventory().contains(Material.ARROW)) {
            p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        }
    }

    public void removeItems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isHeroItem(contents[i])) p.getInventory().setItem(i, null);
        }
    }

    // ---------------------------------------------------------------- passives

    public void applyPassives(Player p, Hero hero) {
        for (PotionEffect effect : hero.passives()) {
            if (!p.hasPotionEffect(effect.getType())) p.addPotionEffect(effect);
        }
    }

    public void removePassives(Player p, Hero hero) {
        for (PotionEffect effect : hero.passives()) {
            PotionEffect current = p.getPotionEffect(effect.getType());
            if (current != null && current.isInfinite()) p.removePotionEffect(effect.getType());
        }
    }

    // ---------------------------------------------------------------- cooldowns

    /** Returns true and starts the cooldown if the ability is ready. */
    public boolean ready(Player p, String ability, long cooldownMs) {
        Map<String, Long> map = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        long until = map.getOrDefault(ability, 0L);
        if (now < until) {
            double secs = (until - now) / 1000.0;
            Fx.bar(p, String.format("Ready in %.1fs", secs), NamedTextColor.RED);
            return false;
        }
        map.put(ability, now + cooldownMs);
        return true;
    }

    // ---------------------------------------------------------------- suppression

    /** Turns off someone's powers for a while (used by Solar Flare against Dark). */
    public void suppress(Player p, long ms) {
        suppressedUntil.put(p.getUniqueId(), System.currentTimeMillis() + ms);
    }

    /** True if the player can't use powers right now (and tells them why). */
    public boolean blocked(Player p) {
        Long until = suppressedUntil.get(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) {
            Fx.bar(p, "Your powers are suppressed!", NamedTextColor.DARK_RED);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Nas bodies

    public boolean isBody(Entity e) {
        return e.getPersistentDataContainer().has(bodyKey, PersistentDataType.STRING);
    }

    public UUID bodyOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(bodyKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** True if the entity is the caster themself or their own Nas disguise. */
    public boolean isSelf(Entity e, Player caster) {
        if (e.equals(caster)) return true;
        return caster.getUniqueId().equals(bodyOwner(e));
    }
}
