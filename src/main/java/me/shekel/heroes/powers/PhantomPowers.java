package me.shekel.heroes.powers;

import me.shekel.heroes.Fx;
import me.shekel.heroes.HeroManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Phantom: a bow that shoots portal-opening arrows. */
public class PhantomPowers {

    public enum Mode { PORTAL, BLINK }

    private static final long PORTAL_LIFETIME_MS = 45000;
    private static final Color BLUE = Color.fromRGB(60, 140, 255);
    private static final Color ORANGE = Color.fromRGB(255, 140, 30);

    private static final class Portal {
        final Location base;   // where entities arrive (feet)
        final Vector right;    // sideways direction for drawing the ring
        final long expires;
        final Color color;

        Portal(Location base, Vector right, long expires, Color color) {
            this.base = base;
            this.right = right;
            this.expires = expires;
            this.color = color;
        }

        Location center() { return base.clone().add(0, 1, 0); }
    }

    private final HeroManager mgr;
    private final Map<UUID, Mode> modes = new HashMap<>();
    private final Map<UUID, Portal[]> portals = new HashMap<>();
    private final Map<UUID, Integer> nextSlot = new HashMap<>();
    private final Map<UUID, Long> entityPortalCooldown = new HashMap<>();
    private final Set<Projectile> flyingArrows = new HashSet<>();

    public PhantomPowers(HeroManager mgr) {
        this.mgr = mgr;
    }

    public Mode mode(Player p) {
        return modes.getOrDefault(p.getUniqueId(), Mode.PORTAL);
    }

    /** F: switch between placing portals and teleporting yourself. */
    public void toggleMode(Player p) {
        Mode next = mode(p) == Mode.PORTAL ? Mode.BLINK : Mode.PORTAL;
        modes.put(p.getUniqueId(), next);
        String text = next == Mode.PORTAL
                ? "Mode: PORTAL - arrows open linked portals"
                : "Mode: BLINK - arrows teleport you where they land";
        Fx.bar(p, text, NamedTextColor.AQUA);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, next == Mode.PORTAL ? 1.2f : 0.8f);
    }

    /** Marks an arrow shot from the Phantom Bow. */
    public void onShoot(Player p, Entity arrow) {
        if (mgr.blocked(p)) return; // shoots a normal arrow
        if (!(arrow instanceof Projectile proj)) return;
        proj.getPersistentDataContainer().set(mgr.arrowKey, PersistentDataType.STRING, p.getUniqueId().toString());
        flyingArrows.add(proj);
    }

    public boolean isPortalArrow(Projectile proj) {
        return proj.getPersistentDataContainer().has(mgr.arrowKey, PersistentDataType.STRING);
    }

    /** Where the arrow lands, open a portal (or teleport Phantom in Blink mode). */
    public void onHit(Player shooter, ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        flyingArrows.remove(proj);

        Location base;
        if (e.getHitBlock() != null) {
            BlockFace face = e.getHitBlockFace() == null ? BlockFace.UP : e.getHitBlockFace();
            Block in = e.getHitBlock().getRelative(face);
            base = in.getLocation().add(0.5, 0, 0.5);
            if (face == BlockFace.DOWN) base.subtract(0, 1, 0); // hit a ceiling: hang below it
        } else if (e.getHitEntity() != null) {
            base = e.getHitEntity().getLocation();
        } else {
            base = proj.getLocation();
        }
        proj.remove();

        if (!shooter.getWorld().equals(base.getWorld())) return;

        World w = base.getWorld();
        if (mode(shooter) == Mode.BLINK) {
            Location dest = base.clone();
            dest.setYaw(shooter.getLocation().getYaw());
            dest.setPitch(shooter.getLocation().getPitch());
            w.spawnParticle(Particle.REVERSE_PORTAL, shooter.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
            shooter.teleport(dest);
            shooter.setFallDistance(0);
            w.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 60, 0.3, 0.6, 0.3, 0.3);
            w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
            return;
        }

        Vector flat = base.toVector().subtract(shooter.getLocation().toVector()).setY(0);
        if (flat.lengthSquared() < 0.01) flat = new Vector(1, 0, 0);
        Vector right = flat.normalize().getCrossProduct(new Vector(0, 1, 0)).normalize();

        UUID id = shooter.getUniqueId();
        int slot = nextSlot.getOrDefault(id, 0);
        Portal[] pair = portals.computeIfAbsent(id, k -> new Portal[2]);
        pair[slot] = new Portal(base, right, System.currentTimeMillis() + PORTAL_LIFETIME_MS, slot == 0 ? BLUE : ORANGE);
        nextSlot.put(id, 1 - slot);

        w.playSound(base, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, slot == 0 ? 1.4f : 0.9f);
        boolean linked = pair[0] != null && pair[1] != null;
        Fx.bar(shooter, (slot == 0 ? "Blue" : "Orange") + " portal opened" + (linked ? " - portals linked!" : ""),
                NamedTextColor.AQUA);
    }

    /** Sneak + left-click: close all your portals. */
    public void closePortals(Player p) {
        if (portals.remove(p.getUniqueId()) != null) {
            Fx.bar(p, "Portals closed", NamedTextColor.AQUA);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1f);
        }
        nextSlot.remove(p.getUniqueId());
    }

    public void clearPortals(Player p) {
        portals.remove(p.getUniqueId());
        nextSlot.remove(p.getUniqueId());
        modes.remove(p.getUniqueId());
    }

    public void tick(long tick) {
        // arrow trails
        Iterator<Projectile> arrows = flyingArrows.iterator();
        while (arrows.hasNext()) {
            Projectile a = arrows.next();
            if (!a.isValid()) { arrows.remove(); continue; }
            a.getWorld().spawnParticle(Particle.PORTAL, a.getLocation(), 4, 0.05, 0.05, 0.05, 0.2);
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Portal[]>> it = portals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Portal[]> en = it.next();
            if (Bukkit.getPlayer(en.getKey()) == null) { it.remove(); continue; }

            Portal[] pair = en.getValue();
            for (int i = 0; i < 2; i++) {
                if (pair[i] != null && now > pair[i].expires) pair[i] = null;
            }
            if (pair[0] == null && pair[1] == null) { it.remove(); continue; }

            if (tick % 3 == 0) {
                for (Portal portal : pair) if (portal != null) draw(portal);
            }
            if (pair[0] != null && pair[1] != null && tick % 2 == 0) {
                checkTeleport(pair[0], pair[1], now);
                checkTeleport(pair[1], pair[0], now);
            }
        }

        if (tick % 200 == 0) entityPortalCooldown.values().removeIf(t -> t < now);
    }

    private void checkTeleport(Portal from, Portal to, long now) {
        Location c = from.center();
        for (Entity e : c.getWorld().getNearbyEntities(c, 0.7, 1.1, 0.7)) {
            if (!(e instanceof LivingEntity) || mgr.isBody(e)) continue;
            Long until = entityPortalCooldown.get(e.getUniqueId());
            if (until != null && now < until) continue;

            Location dest = to.base.clone();
            dest.setYaw(e.getLocation().getYaw());
            dest.setPitch(e.getLocation().getPitch());
            e.teleport(dest);
            e.setFallDistance(0);
            entityPortalCooldown.put(e.getUniqueId(), now + 1500);

            c.getWorld().playSound(c, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
            dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
            dest.getWorld().spawnParticle(Particle.PORTAL, to.center(), 40, 0.3, 0.6, 0.3, 0.3);
        }
    }

    private void draw(Portal portal) {
        Location c = portal.center();
        World w = c.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(portal.color, 1.2f);
        int points = 20;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Vector off = portal.right.clone().multiply(Math.cos(a) * 0.6).add(new Vector(0, Math.sin(a) * 1.0, 0));
            w.spawnParticle(Particle.DUST, c.getX() + off.getX(), c.getY() + off.getY(), c.getZ() + off.getZ(),
                    1, 0, 0, 0, 0, dust);
        }
        w.spawnParticle(Particle.PORTAL, c, 3, 0.2, 0.5, 0.2, 0.1);
    }
}
