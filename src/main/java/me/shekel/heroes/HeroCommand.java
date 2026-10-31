package me.shekel.heroes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /hero                  - info and hero list
 * /hero <name>           - become a hero
 * /hero none             - stop being a hero
 * /hero item             - get your power item back
 * /hero set <player> <name|none>  - admins only
 */
public class HeroCommand implements TabExecutor {

    private final HeroManager mgr;

    public HeroCommand(HeroManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            return setOther(sender, args);
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only. Console can use: hero set <player> <hero|none>");
            return true;
        }

        if (args.length == 0) {
            showInfo(p);
            return true;
        }

        String arg = args[0].toLowerCase(Locale.ROOT);
        switch (arg) {
            case "none", "off", "stop" -> mgr.setHero(p, null);
            case "item", "items" -> {
                Hero hero = mgr.getHero(p);
                if (hero == null) {
                    Fx.msg(p, "You aren't a hero yet. Try /hero solar", NamedTextColor.RED);
                } else {
                    mgr.giveItems(p, hero);
                    Fx.msg(p, "Power item restored.", hero.color());
                }
            }
            default -> {
                Hero hero = Hero.parse(arg);
                if (hero == null) {
                    Fx.msg(p, "Unknown hero. Choose: solar, dark, phantom, nas", NamedTextColor.RED);
                } else {
                    mgr.setHero(p, hero);
                }
            }
        }
        return true;
    }

    private boolean setOther(CommandSender sender, String[] args) {
        if (!sender.hasPermission("heroes.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /hero set <player> <solar|dark|phantom|nas|none>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        Hero hero = Hero.parse(args[2]);
        if (hero == null && !args[2].equalsIgnoreCase("none")) {
            sender.sendMessage(Component.text("Unknown hero.", NamedTextColor.RED));
            return true;
        }
        mgr.setHero(target, hero);
        sender.sendMessage(Component.text("Set " + target.getName() + " to "
                + (hero == null ? "no hero" : hero.displayName()) + ".", NamedTextColor.GREEN));
        return true;
    }

    private void showInfo(Player p) {
        Hero current = mgr.getHero(p);
        p.sendMessage(Component.text("=== Heroes ===", NamedTextColor.YELLOW, TextDecoration.BOLD));
        p.sendMessage(Component.text("You are: ", NamedTextColor.GRAY)
                .append(current == null
                        ? Component.text("nobody (yet)", NamedTextColor.WHITE)
                        : Component.text(current.displayName(), current.color())));
        for (Hero h : Hero.values()) {
            p.sendMessage(Component.text(" /hero " + h.name().toLowerCase(Locale.ROOT), h.color())
                    .append(Component.text(" - " + h.controls().get(0) + ", ...", NamedTextColor.GRAY)));
        }
        p.sendMessage(Component.text(" /hero none", NamedTextColor.WHITE)
                .append(Component.text(" - stop being a hero", NamedTextColor.GRAY)));
        p.sendMessage(Component.text(" /hero item", NamedTextColor.WHITE)
                .append(Component.text(" - get your power item back", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Hero h : Hero.values()) out.add(h.name().toLowerCase(Locale.ROOT));
            out.add("none");
            out.add("item");
            if (sender.hasPermission("heroes.admin")) out.add("set");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (Hero h : Hero.values()) out.add(h.name().toLowerCase(Locale.ROOT));
            out.add("none");
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
