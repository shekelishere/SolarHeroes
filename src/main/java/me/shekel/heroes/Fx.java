package me.shekel.heroes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Small helpers for particles and messages. */
public final class Fx {

    private Fx() {}

    /** Draws a straight particle line from start to end. */
    public static void line(Location start, Vector end, Particle particle, double step) {
        World w = start.getWorld();
        Vector from = start.toVector();
        Vector diff = end.clone().subtract(from);
        double len = diff.length();
        if (len < 0.01) return;
        Vector dir = diff.normalize();
        for (double d = 0; d <= len; d += step) {
            Vector p = from.clone().add(dir.clone().multiply(d));
            w.spawnParticle(particle, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** Flat ring of particles around a center. */
    public static void ring(Location center, double radius, Particle particle, int points) {
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            w.spawnParticle(particle,
                    center.getX() + Math.cos(a) * radius,
                    center.getY(),
                    center.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    public static void bar(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    public static void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text(text, color));
    }
}
