package me.shekel.heroes.powers;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.shekel.heroes.Fx;
import me.shekel.heroes.HeroManager;
import me.shekel.heroes.SolarHeroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Nas: a shapeshifter who can become any mob or copy another player. */
public class NasPowers {

    private static final EntityType[] FORMS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.ENDERMAN,
            EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.WOLF, EntityType.COW
    };

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };

    private static final class Disguise {
        final LivingEntity body;        // mob form (null for player form)
        final PlayerProfile original;   // original skin/name (null for mob form)
        final String form;

        Disguise(LivingEntity body, PlayerProfile original, String form) {
            this.body = body;
            this.original = original;
            this.form = form;
        }
    }

    private final SolarHeroes plugin;
    private final HeroManager mgr;
    private final Map<UUID, Disguise> disguises = new HashMap<>();
    private final Map<UUID, Integer> formIndex = new HashMap<>();
    private final Map<UUID, Long> lastEntityClick = new HashMap<>();

    public NasPowers(SolarHeroes plugin, HeroManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ---------------------------------------------------------------- controls

    /** Right-click a mob or player: take their form. */
    public void copy(Player p, Entity target) {
        lastEntityClick.put(p.getUniqueId(), System.currentTimeMillis());
        if (mgr.blocked(p)) return;

        if (mgr.isBody(target)) {
            // Clicking another Nas in disguise copies their mob form
            shiftToMob(p, target.getType());
        } else if (target instanceof Player other) {
            shiftToPlayer(p, other);
        } else if (target instanceof LivingEntity) {
            shiftToMob(p, target.getType());
        }
    }

    /** True right after clicking an entity (so the same click doesn't also cycle forms). */
    public boolean justClickedEntity(Player p) {
        Long t = lastEntityClick.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 250;
    }

    /** Right-click air: cycle through preset forms. */
    public void cycle(Player p, int delta) {
        if (mgr.blocked(p)) return;
        int idx = formIndex.getOrDefault(p.getUniqueId(), -1);
        idx = Math.floorMod(idx + delta, FORMS.length);
        formIndex.put(p.getUniqueId(), idx);
        shiftToMob(p, FORMS[idx]);
    }

    /** F: back to normal. */
    public void revert(Player p) {
        if (!disguises.containsKey(p.getUniqueId())) {
            Fx.bar(p, "You're already in your own form", NamedTextColor.GREEN);
            return;
        }
        unshift(p, true);
    }

    // ---------------------------------------------------------------- shifting

    private void shiftToMob(Player p, EntityType type) {
        if (type == EntityType.PLAYER || !type.isAlive() || !type.isSpawnable()
                || type == EntityType.ENDER_DRAGON || type == EntityType.WITHER) {
            Fx.bar(p, "You can't shapeshift into that", NamedTextColor.RED);
            return;
        }
        if (!mgr.ready(p, "nas_shift", 1500)) return;

        unshift(p, false);

        Entity spawned = p.getWorld().spawnEntity(p.getLocation(), type);
        if (!(spawned instanceof LivingEntity body)) {
            spawned.remove();
            Fx.bar(p, "You can't shapeshift into that", NamedTextColor.RED);
            return;
        }

        body.getPersistentDataContainer().set(mgr.bodyKey, PersistentDataType.STRING, p.getUniqueId().toString());
        body.setAI(false);
        body.setSilent(true);
        body.setPersistent(false);
        body.setRemoveWhenFarAway(false);
        body.setCollidable(false);
        body.setCanPickupItems(false);
        if (body instanceof Ageable ageable) ageable.setAdult();
        if (body instanceof Mob mob) mob.setTarget(null);

        p.setInvisible(true);
        p.hideEntity(plugin, body); // Nas doesn't see their own disguise in their face

        String name = prettyName(type);
        disguises.put(p.getUniqueId(), new Disguise(body, null, name));
        hideEquipment(p);

        poof(p.getLocation());
        Fx.bar(p, "You are now a " + name, NamedTextColor.GREEN);
    }

    private void shiftToPlayer(Player p, Player target) {
        if (target.equals(p)) return;
        if (!mgr.ready(p, "nas_shift", 1500)) return;

        unshift(p, false);

        PlayerProfile original = p.getPlayerProfile();
        PlayerProfile fake = Bukkit.createProfile(p.getUniqueId(), target.getName());
        fake.setProperties(target.getPlayerProfile().getProperties());
        p.setPlayerProfile(fake);
        p.displayName(Component.text(target.getName()));
        p.playerListName(Component.text(target.getName()));

        disguises.put(p.getUniqueId(), new Disguise(null, original, target.getName()));
        poof(p.getLocation());
        Fx.bar(p, "You now look exactly like " + target.getName(), NamedTextColor.GREEN);
    }

    public void unshift(Player p, boolean announce) {
        Disguise d = disguises.remove(p.getUniqueId());
        if (d == null) return;

        if (d.body != null) {
            d.body.remove();
            p.setInvisible(false);
            showEquipment(p);
        }
        if (d.original != null) {
            p.setPlayerProfile(d.original);
            p.displayName(null);
            p.playerListName(null);
        }
        if (announce) {
            poof(p.getLocation());
            Fx.bar(p, "Back to your own form", NamedTextColor.GREEN);
        }
    }

    /** Called when a disguise body somehow dies. */
    public void bodyDied(UUID owner) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) unshift(p, true);
        else disguises.remove(owner);
    }

    public void unshiftAll() {
        for (UUID id : disguises.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) unshift(p, false);
        }
        disguises.clear();
    }

    // ---------------------------------------------------------------- per tick

    public void tick(long tick) {
        Iterator<Map.Entry<UUID, Disguise>> it = disguises.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Disguise> en = it.next();
            Disguise d = en.getValue();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                if (d.body != null) d.body.remove();
                it.remove();
                continue;
            }
            if (d.body == null) continue;
            if (!d.body.isValid()) {
                // body was removed by something else: restore the player
                it.remove();
                p.setInvisible(false);
                showEquipment(p);
                continue;
            }
            d.body.teleport(p.getLocation());
            if (tick % 10 == 0) hideEquipment(p);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Hides Nas' armor and held items from everyone else while in mob form. */
    private void hideEquipment(Player p) {
        ItemStack air = new ItemStack(Material.AIR);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p) || !viewer.getWorld().equals(p.getWorld())) continue;
            for (EquipmentSlot slot : SLOTS) viewer.sendEquipmentChange(p, slot, air);
        }
    }

    private void showEquipment(Player p) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p) || !viewer.getWorld().equals(p.getWorld())) continue;
            for (EquipmentSlot slot : SLOTS) viewer.sendEquipmentChange(p, slot, p.getEquipment().getItem(slot));
        }
    }

    private static void poof(Location loc) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.03);
        loc.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
    }

    private static String prettyName(EntityType type) {
        String s = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String part : s.split(" ")) {
            if (part.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }
}
