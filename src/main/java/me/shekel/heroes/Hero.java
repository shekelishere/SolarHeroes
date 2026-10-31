package me.shekel.heroes;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public enum Hero {

    SOLAR("Solar", NamedTextColor.GOLD, Material.BLAZE_ROD, "Solar Core",
            List.of("Right-click: Solar Beam",
                    "Sneak + Right-click: Solar Flare",
                    "F (swap hands): Solar Flight")),

    DARK("Dark", NamedTextColor.DARK_PURPLE, Material.ECHO_SHARD, "Dark Shard",
            List.of("Right-click: Shadow Bolt",
                    "Sneak + Right-click: Darkness Wave",
                    "F (swap hands): Shadow Step")),

    PHANTOM("Phantom", NamedTextColor.AQUA, Material.BOW, "Phantom Bow",
            List.of("Shoot: Portal Arrow",
                    "F (swap hands): switch Portal / Blink mode",
                    "Sneak + Left-click: close your portals")),

    NAS("Nas", NamedTextColor.GREEN, Material.AMETHYST_SHARD, "Shapeshift Mask",
            List.of("Right-click a mob or player: become them",
                    "Right-click air: next form (Sneak = previous)",
                    "F (swap hands): back to normal"));

    private final String displayName;
    private final NamedTextColor color;
    private final Material itemType;
    private final String itemName;
    private final List<String> controls;

    Hero(String displayName, NamedTextColor color, Material itemType, String itemName, List<String> controls) {
        this.displayName = displayName;
        this.color = color;
        this.itemType = itemType;
        this.itemName = itemName;
        this.controls = controls;
    }

    public String displayName() { return displayName; }
    public NamedTextColor color() { return color; }
    public Material itemType() { return itemType; }
    public String itemName() { return itemName; }
    public List<String> controls() { return controls; }

    /** Permanent effects the hero always has while active. */
    public List<PotionEffect> passives() {
        int inf = PotionEffect.INFINITE_DURATION;
        return switch (this) {
            case SOLAR -> List.of(
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, inf, 0, true, false, true),
                    new PotionEffect(PotionEffectType.NIGHT_VISION, inf, 0, true, false, true));
            case DARK -> List.of(
                    new PotionEffect(PotionEffectType.NIGHT_VISION, inf, 0, true, false, true),
                    new PotionEffect(PotionEffectType.STRENGTH, inf, 0, true, false, true));
            case PHANTOM -> List.of(
                    new PotionEffect(PotionEffectType.SPEED, inf, 0, true, false, true),
                    new PotionEffect(PotionEffectType.JUMP_BOOST, inf, 0, true, false, true));
            case NAS -> List.of(
                    new PotionEffect(PotionEffectType.SPEED, inf, 0, true, false, true));
        };
    }

    public static Hero parse(String s) {
        if (s == null) return null;
        for (Hero h : values()) {
            if (h.name().equalsIgnoreCase(s) || h.displayName.equalsIgnoreCase(s)) return h;
        }
        return null;
    }
}
