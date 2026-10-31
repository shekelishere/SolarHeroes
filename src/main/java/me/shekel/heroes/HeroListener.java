package me.shekel.heroes;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Turns clicks and key presses into hero powers. */
public class HeroListener implements Listener {

    private final SolarHeroes plugin;
    private final HeroManager mgr;

    public HeroListener(SolarHeroes plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Hero itemHero = mgr.itemHero(e.getItem());
        if (itemHero == null) return;

        Hero hero = mgr.getHero(p);
        if (hero != itemHero) {
            if (hero == null) Fx.bar(p, "Become a hero first: /hero " + itemHero.name().toLowerCase(), NamedTextColor.RED);
            return;
        }

        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;

        switch (hero) {
            case SOLAR -> {
                if (!right) return;
                e.setCancelled(true);
                if (p.isSneaking()) plugin.solar().flare(p);
                else plugin.solar().beam(p);
            }
            case DARK -> {
                if (!right) return;
                e.setCancelled(true);
                if (p.isSneaking()) plugin.dark().wave(p);
                else plugin.dark().bolt(p);
            }
            case NAS -> {
                if (!right) return;
                e.setCancelled(true);
                if (plugin.nas().justClickedEntity(p)) return;
                plugin.nas().cycle(p, p.isSneaking() ? -1 : 1);
            }
            case PHANTOM -> {
                // right-click draws the bow normally
                if (left && p.isSneaking()) {
                    e.setCancelled(true);
                    plugin.phantom().closePortals(p);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (mgr.itemHero(item) != Hero.NAS || mgr.getHero(p) != Hero.NAS) return;
        e.setCancelled(true);
        plugin.nas().copy(p, e.getRightClicked());
    }

    /** F key: each hero's special move. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Hero itemHero = mgr.itemHero(p.getInventory().getItemInMainHand());
        if (itemHero == null || mgr.getHero(p) != itemHero) return;
        e.setCancelled(true);

        switch (itemHero) {
            case SOLAR -> plugin.solar().toggleFlight(p);
            case DARK -> plugin.dark().shadowStep(p);
            case PHANTOM -> plugin.phantom().toggleMode(p);
            case NAS -> plugin.nas().revert(p);
        }
    }

    // ---------------------------------------------------------------- Phantom bow

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (mgr.itemHero(e.getBow()) != Hero.PHANTOM || mgr.getHero(p) != Hero.PHANTOM) return;
        plugin.phantom().onShoot(p, e.getProjectile());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!plugin.phantom().isPortalArrow(proj)) return;
        if (!(proj.getShooter() instanceof Player shooter)) return;
        if (mgr.getHero(shooter) != Hero.PHANTOM) return;
        plugin.phantom().onHit(shooter, e);
    }

    // ---------------------------------------------------------------- Nas disguise bodies

    @EventHandler(priority = EventPriority.LOW)
    public void onBodyDamage(EntityDamageEvent e) {
        Entity body = e.getEntity();
        if (!mgr.isBody(body)) return;
        e.setCancelled(true);

        if (e instanceof EntityDamageByEntityEvent byEntity) {
            UUID ownerId = mgr.bodyOwner(body);
            Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
            Entity damager = byEntity.getDamager();
            if (owner != null && !owner.equals(damager)) {
                // hitting the disguise hurts Nas
                owner.damage(e.getDamage(), damager);
            }
        }
    }

    @EventHandler
    public void onBodyCombust(EntityCombustEvent e) {
        if (mgr.isBody(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onBodyDeath(EntityDeathEvent e) {
        if (!mgr.isBody(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        UUID owner = mgr.bodyOwner(e.getEntity());
        if (owner != null) plugin.nas().bodyDied(owner);
    }

    // ---------------------------------------------------------------- player lifecycle

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (mgr.isHeroItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            Fx.bar(e.getPlayer(), "You can't drop your power. Use /hero none to stop being a hero.", NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.getDrops().removeIf(mgr::isHeroItem);
        plugin.nas().unshift(p, false);
        plugin.solar().endFlight(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Hero hero = mgr.getHero(p);
            if (hero == null || !p.isOnline()) return;
            mgr.giveItems(p, hero);
            mgr.applyPassives(p, hero);
        }, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Hero hero = mgr.getHero(p);
        if (hero == null) return;
        mgr.giveItems(p, hero);
        mgr.applyPassives(p, hero);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.nas().unshift(p, false);
        plugin.solar().endFlight(p);
        plugin.phantom().clearPortals(p);
    }
}
