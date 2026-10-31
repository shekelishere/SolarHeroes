package me.shekel.heroes.powers;

import me.shekel.heroes.Fx;
import me.shekel.heroes.Hero;
import me.shekel.heroes.HeroManager;
import me.shekel.heroes.SolarHeroes;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Dark: Solar's brother, master of shadow. */
public class DarkPowers {

    private static final double BOLT_RANGE = 25;
    private static final double STEP_RANGE = 15;
    private static final double WAVE_RADIUS = 8;

    private final SolarHeroes plugin;
    private final HeroManager mgr;

    public DarkPowers(SolarHeroes plugin, HeroManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    /** Right-click: a bolt of shadow that blinds whoever it hits. */
    public void bolt(Player p) {
        if (mgr.blocked(p) || !mgr.ready(p, "dark_bolt", 2000)) return;

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.getWorld().rayTrace(eye, dir, BOLT_RANGE, FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !mgr.isSelf(e, p));

        Vector end = hit != null ? hit.getHitPosition() : eye.toVector().add(dir.clone().multiply(BOLT_RANGE));
        Fx.line(eye, end, Particle.SQUID_INK, 0.4);
        Fx.line(eye, end, Particle.SMOKE, 0.6);
        p.getWorld().playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.8f, 0.7f);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(5, p);
            target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            if (absorbedBySolar(target, p)) return;
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
        }
    }

    /** Sneak + right-click: a wave of darkness that slows and weakens everyone around Dark. */
    public void wave(Player p) {
        if (mgr.blocked(p) || !mgr.ready(p, "dark_wave", 18000)) return;

        Location center = p.getLocation();
        World w = p.getWorld();

        for (Entity e : w.getNearbyEntities(center, WAVE_RADIUS, WAVE_RADIUS, WAVE_RADIUS)) {
            if (!(e instanceof LivingEntity target) || e instanceof ArmorStand || mgr.isSelf(e, p)) continue;
            if (target.getLocation().distance(center) > WAVE_RADIUS) continue;
            if (absorbedBySolar(target, p)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
        }

        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.6f);
        // expanding ring of shadow
        new BukkitRunnable() {
            double r = 0.5;

            @Override
            public void run() {
                Location ring = center.clone().add(0, 0.3, 0);
                Fx.ring(ring, r, Particle.LARGE_SMOKE, (int) (r * 12));
                Fx.ring(ring, r, Particle.SQUID_INK, (int) (r * 6));
                r += 1;
                if (r > WAVE_RADIUS) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** F: teleport through the shadows to where Dark is looking. */
    public void shadowStep(Player p) {
        if (mgr.blocked(p)) return;

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = p.rayTraceBlocks(STEP_RANGE);
        Vector target = hit != null
                ? hit.getHitPosition().subtract(dir.clone().multiply(0.8))
                : eye.toVector().add(dir.clone().multiply(STEP_RANGE));

        Location dest = target.toLocation(p.getWorld(), p.getLocation().getYaw(), p.getLocation().getPitch());
        dest.setY(Math.floor(dest.getY()));
        if (!fits(dest)) dest.add(0, 1, 0);
        if (!fits(dest)) {
            Fx.bar(p, "No room to step there", NamedTextColor.DARK_PURPLE);
            return;
        }
        if (!mgr.ready(p, "dark_step", 6000)) return;

        Location from = p.getLocation();
        from.getWorld().spawnParticle(Particle.LARGE_SMOKE, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
        p.teleport(dest);
        p.setFallDistance(0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, false));
        dest.getWorld().spawnParticle(Particle.LARGE_SMOKE, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
        dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    /** Solar can absorb darkness: he takes the hit but none of the blinding effects. */
    private boolean absorbedBySolar(LivingEntity target, Player dark) {
        if (target instanceof Player other && mgr.getHero(other) == Hero.SOLAR) {
            Fx.bar(other, "You absorbed the darkness!", NamedTextColor.GOLD);
            Fx.bar(dark, "Solar absorbed your darkness...", NamedTextColor.DARK_PURPLE);
            other.getWorld().spawnParticle(Particle.END_ROD, other.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
            return true;
        }
        return false;
    }

    private static boolean fits(Location feet) {
        Block b = feet.getBlock();
        return b.isPassable() && b.getRelative(0, 1, 0).isPassable();
    }
}
