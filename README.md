# SolarHeroes

Paper **26.2** plugin. Anyone on the server can become Solar, Dark, Phantom or Nas.
Players don't install anything, and 1.21.11 players can use it too (through ViaVersion + ViaBackwards).

## Commands

| Command | What it does |
|---|---|
| `/hero` | Shows the hero list and which hero you are |
| `/hero solar` / `dark` / `phantom` / `nas` | Become that hero (you get the power item) |
| `/hero none` | Stop being a hero |
| `/hero item` | Get your power item back |
| `/hero set <player> <hero/none>` | Ops only: change someone else's hero |

## Powers

**Solar** (Solar Core) - always has Fire Resistance + Night Vision
- Right-click: **Solar Beam** - burning beam of light, 30 blocks
- Sneak + Right-click: **Solar Flare** - blast of light that pushes and burns everyone nearby. If it hits Dark, it absorbs his power for 6 seconds
- F: **Solar Flight** - fly for 8 seconds (press F again to land)
- Dark's blinding effects don't work on Solar - he absorbs the darkness

**Dark** (Dark Shard) - always has Night Vision + Strength
- Right-click: **Shadow Bolt** - damage + blindness
- Sneak + Right-click: **Darkness Wave** - darkness, slowness and weakness on everyone within 8 blocks
- F: **Shadow Step** - teleport through the shadows up to 15 blocks

**Phantom** (Phantom Bow) - always has Speed + Jump Boost
- Shoot: **Portal Arrow** - first arrow opens a blue portal, second an orange one. Anyone who walks into one comes out of the other. Portals last 45 seconds
- F (holding the bow): switch to **Blink** mode - arrows teleport you where they land
- Sneak + Left-click: close your portals

**Nas** (Shapeshift Mask) - always has Speed
- Right-click a mob: become that mob
- Right-click a player: copy their skin and name
- Right-click air: next form (Sneak = previous): Zombie, Skeleton, Creeper, Enderman, Villager, Iron Golem, Wolf, Cow
- F: back to normal
- Hitting Nas' disguise hurts Nas

## Building the .jar with GitHub (no install needed)

1. Create a new GitHub repository.
2. Upload `pom.xml`, `README.md` and the `src` folder (keep the folder structure).
3. Add the build file: **Add file → Create new file**, name it exactly `.github/workflows/build.yml`, paste the contents of `build.yml` from this zip, and commit.
4. Open the **Actions** tab, wait for the green check, open the run, and download **SolarHeroes** under Artifacts.
5. Unzip it and put `SolarHeroes-1.0.0.jar` into your server's `plugins` folder. Restart the server.
