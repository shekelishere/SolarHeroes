package me.shekel.heroes.powers;

import me.shekel.heroes.Fx;
import me.shekel.heroes.Hero;
import me.shekel.heroes.HeroManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Solar: a scientist who controls light and can absorb darkness. */
public class SolarPowers {

    private static final double BEAM_RANGE = 30;
    private static final long FLIGHT_MS = 8000;

    private final HeroManager mgr;
    private final Map<UUID, Long> flightEnd = new HashMap<>();

    public SolarPowers(HeroManager mgr) {
        this.mgr = mgr;
    }

    /** Right-click: a beam of sunlight that burns the first thing it hits. */
    public void beam(Player p) {
        if (mgr.blocked(p) || !mgr.ready(p, "solar_beam", 1500)) return;

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTrace(eye, dir, BEAM_RANGE, FluidCollisionMode.NEVER, true, 0.4,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !mgr.isSelf(e, p));

        Vector end = hit != null ? hit.getHitPosition() : eye.toVector().add(dir.clone().multiply(BEAM_RANGE));
        Fx.line(eye, end, Particle.END_ROD, 0.35);
        Fx.line(eye, end, Particle.FLAME, 0.7);
        p.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1f, 1.6f);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(6, p);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Sneak + right-click: a burst of light around Solar. Absorbs Dark's power. */
    public void flare(Player p) {
        if (mgr.blocked(p) || !mgr.ready(p, "solar_flare", 15000)) return;

        Location center = p.getLocation();
        World w = p.getWorld();

        for (Entity e : w.getNearbyEntities(center, 6, 6, 6)) {
            if (!(e instanceof LivingEntity target) || e instanceof ArmorStand || mgr.isSelf(e, p)) continue;
            if (target.getLocation().distance(center) > 6) continue;

            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(0, 0, 0.1);
            target.setVelocity(push.normalize().multiply(1.2).setY(0.5));
            target.damage(3, p);
            target.setFireTicks(Math.max(target.getFireTicks(), 60));

            if (target instanceof Player other && mgr.getHero(other) == Hero.DARK) {
                mgr.suppress(other, 6000);
                Fx.msg(other, "Solar absorbed your darkness! Powers gone for 6 seconds.", NamedTextColor.GOLD);
                Fx.bar(p, "You absorbed Dark's power!", NamedTextColor.GOLD);
            }
        }

        // Light cleanses Solar
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.WEAKNESS);

        Location mid = center.clone().add(0, 1, 0);
        w.spawnParticle(Particle.FLAME, mid, 200, 2.5, 1.2, 2.5, 0.08);
        w.spawnParticle(Particle.END_ROD, mid, 120, 2.5, 1.2, 2.5, 0.1);
        for (double r = 1; r <= 6; r += 1) Fx.ring(center.clone().add(0, 0.2, 0), r, Particle.END_ROD, (int) (r * 10));
        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.8f);
        w.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
    }

    /** F: fly for a few seconds. Press again to land early. */
    public void toggleFlight(Player p) {
        if (flightEnd.containsKey(p.getUniqueId())) {
            endFlight(p);
            return;
        }
        if (mgr.blocked(p) || !mgr.ready(p, "solar_flight", 20000)) return;

        flightEnd.put(p.getUniqueId(), System.currentTimeMillis() + FLIGHT_MS);
        p.setAllowFlight(true);
        p.setVelocity(p.getVelocity().setY(0.6));
        p.setFlying(true);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 40, 0.3, 0.1, 0.3, 0.1);
    }

    public void endFlight(Player p) {
        if (flightEnd.remove(p.getUniqueId()) == null) return;
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(false);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false, true));
        }
        Fx.bar(p, "Solar Flight ended", NamedTextColor.GOLD);
    }

    public void endAll() {
        for (UUID id : flightEnd.keySet().toArray(new UUID[0])) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) endFlight(p);
        }
        flightEnd.clear();
    }

    public void tick(long tick) {
        long now = System.currentTimeMillis();
        List<Player> expired = new ArrayList<>();
        Iterator<Map.Entry<UUID, Long>> it = flightEnd.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) { it.remove(); continue; }
            if (now >= en.getValue()) {
                expired.add(p);
                continue;
            }
            if (tick % 2 == 0) {
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 3, 0.15, 0, 0.15, 0.01);
            }
            if (tick % 10 == 0) {
                Fx.bar(p, String.format("Solar Flight: %.0fs", Math.ceil((en.getValue() - now) / 1000.0)), NamedTextColor.GOLD);
            }
        }
        for (Player p : expired) endFlight(p);
    }
}
