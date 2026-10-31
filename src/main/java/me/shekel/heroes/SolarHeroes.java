package me.shekel.heroes;

import me.shekel.heroes.powers.DarkPowers;
import me.shekel.heroes.powers.NasPowers;
import me.shekel.heroes.powers.PhantomPowers;
import me.shekel.heroes.powers.SolarPowers;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** SolarHeroes - play as Solar, Dark, Phantom and Nas. */
public class SolarHeroes extends JavaPlugin {

    private HeroManager manager;
    private SolarPowers solar;
    private DarkPowers dark;
    private PhantomPowers phantom;
    private NasPowers nas;
    private long tick;

    @Override
    public void onEnable() {
        manager = new HeroManager(this);
        solar = new SolarPowers(manager);
        dark = new DarkPowers(this, manager);
        phantom = new PhantomPowers(manager);
        nas = new NasPowers(this, manager);

        // Remove disguise bodies left over from a crash
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (manager.isBody(e)) e.remove();
            }
        }

        getServer().getPluginManager().registerEvents(new HeroListener(this), this);

        PluginCommand cmd = getCommand("hero");
        if (cmd != null) {
            HeroCommand executor = new HeroCommand(manager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // Re-apply passives for players already online (e.g. after /reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            Hero h = manager.getHero(p);
            if (h != null) manager.applyPassives(p, h);
        }

        Bukkit.getScheduler().runTaskTimer(this, this::onTick, 1L, 1L);
        getLogger().info("SolarHeroes enabled - /hero to begin.");
    }

    @Override
    public void onDisable() {
        if (nas != null) nas.unshiftAll();
        if (solar != null) solar.endAll();
    }

    private void onTick() {
        tick++;
        solar.tick(tick);
        phantom.tick(tick);
        nas.tick(tick);

        // keep passives on (milk, death, etc.)
        if (tick % 40 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Hero h = manager.getHero(p);
                if (h != null) manager.applyPassives(p, h);
            }
        }
    }

    public HeroManager manager() { return manager; }
    public SolarPowers solar() { return solar; }
    public DarkPowers dark() { return dark; }
    public PhantomPowers phantom() { return phantom; }
    public NasPowers nas() { return nas; }
}
