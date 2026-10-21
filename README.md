# NpcLib

A fully packet based NPC library for **Spigot 1.8.8**.

NPCs are never added to the server world. They only exist as packets, so they do not tick,
do not collide, cannot be pushed or killed and never despawn, while every player sees them
exactly like a normal player.

## Features

- Packet-only NPCs with a player skin (`GameProfile` textures)
- Vanilla name tag toggle, implemented with a per-NPC scoreboard team
- Multi line hologram (invisible armour stands) above the NPC, shown instead of the name tag
- Equipment: hand, helmet, chestplate, leggings and boots
- `turnToPlayer` client side head and body rotation (with a configurable radius)
- Visible for all players, including players who join later or change worlds
- Hidden from the tab list (configurable delay)
- Per-player `show` / `hide`
- Async Mojang skin lookup helper with caching

## Requirements

| | |
|---|---|
| Server | Spigot / Paper **1.8.8** (NMS `v1_8_R3`) |
| Java | 8+ |
| Build | Maven |

## Build

```bash
mvn clean package
```

The resulting jar is `target/NpcLib-1.0.0.jar`.

The build needs the `craftbukkit` 1.8.8 artifact, which is provided by the CodeMC NMS
repository (already configured in `pom.xml`). It is `provided`, so it is not shaded into
the jar.

## Using it as a plugin

Drop `NpcLib-1.0.0.jar` into your `plugins` folder. The plugin registers the demo command
`/npclib` (permission `npclib.admin`) and initialises the library for you.

```
/npclib create [name...]      create an NPC at your position (with your skin)
/npclib remove                remove the nearest NPC
/npclib name <text...>        set the display name, & colour codes work
/npclib nametoggle            toggle the vanilla name tag
/npclib skin <player>         apply the skin of a Mojang account
/npclib holo <text...>        replace the hologram with one line
/npclib addline <text...>     add a hologram line
/npclib line <index> <text>   edit a line (0 = top)
/npclib removeline <index>    remove a line
/npclib holotoggle            show/hide the hologram
/npclib item <slot>           equip the item you are holding
/npclib look                  toggle turn-to-player
/npclib here                  move the nearest NPC to you
/npclib list                  list all NPCs
```

## Using it as a library

Either shade the jar into your plugin, or depend on it and call `NpcLib.init(this)` in
`onEnable()` (it registers the listeners and starts the look task) and
`NpcLib.shutdown()` in `onDisable()`.

```java
public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        NpcLib.init(this);

        Location location = new Location(Bukkit.getWorld("world"), 100.5, 65.0, 200.5);
        Npc npc = NpcLib.createNpc("&aBanker", location);

        // Multi line hologram, shown instead of the vanilla name tag (default).
        npc.setHologramLines("&eBanker", "&7Right-click to trade");
        npc.getHologram().setOffset(2.0D).setLineSpacing(0.25D);

        // Skin of a real account, resolved off the main thread.
        SkinFetcher.fetchAsync(this, "Notch",
                new Consumer<NpcSkin>() {
                    @Override
                    public void accept(NpcSkin skin) {
                        npc.setSkin(skin);
                    }
                },
                new Consumer<IOException>() {
                    @Override
                    public void accept(IOException error) {
                        getLogger().warning("Skin lookup failed: " + error.getMessage());
                    }
                });

        // Equipment
        npc.setItem(NpcSlot.HAND, new ItemStack(Material.DIAMOND_SWORD));
        npc.setItem(NpcSlot.HELMET, new ItemStack(Material.DIAMOND_HELMET));
        npc.setItem(NpcSlot.CHESTPLATE, new ItemStack(Material.IRON_CHESTPLATE));

        // Look at nearby players
        npc.setTurnToPlayer(true);
        npc.setLookRadius(32.0D);

        // Show the vanilla name tag instead of (or in addition to) the hologram
        npc.setNameVisible(true);
    }

    @Override
    public void onDisable() {
        NpcLib.shutdown();
    }
}
```

## API overview

### `NpcLib`

| Method | Description |
|---|---|
| `init(Plugin)` | Initialises the library, listeners and the look task |
| `createNpc(String name, Location location)` | Creates and shows an NPC |
| `getNpc(int entityId)` / `getNpc(UUID)` | Looks an NPC up |
| `getNpcs()` | All registered NPCs |
| `showAll(Player)` / `respawnAll(Player)` / `forget(Player)` | Lifecycle helpers |
| `setHideFromTabList(boolean)` | Enable/disable tab list hiding |
| `setTabListRemovalDelay(int ticks)` | Delay before the tab entry is removed (default 20) |
| `shutdown()` | Removes every NPC and stops tasks |

### `Npc`

| Method | Description |
|---|---|
| `setName(String)` | Display name, supports `&` colours |
| `setNameVisible(boolean)` | Toggles the vanilla name tag |
| `setSkin(NpcSkin)` / `setSkin(String value, String signature)` | Applies a skin |
| `setItem(NpcSlot, ItemStack)` | Equips an item (`null` clears the slot) |
| `clearEquipment()` | Removes all equipment |
| `setHologramLines(String...)` | Convenience for `getHologram().setLines(...)` |
| `setHologramVisible(boolean)` | Shows/hides the hologram |
| `setTurnToPlayer(boolean)` | Client side looking at players |
| `setLookRadius(double)` | Radius used by `turnToPlayer` |
| `teleport(Location)` | Moves the NPC (also across worlds) |
| `show(Player)` / `hide(Player)` / `isShownTo(Player)` | Per-player visibility |
| `remove()` | Removes the NPC for everyone |
| `getEntityId()` | Entity id, handy for packet based click detection |

### `Hologram`

`setLines(String...)`, `setLines(List<String>)`, `addLine`, `insertLine`, `setLine`,
`removeLine`, `clearLines`, `setVisible`, `setOffset` (height of the bottom line above the
NPC's feet, default `2.0`), `setLineSpacing` (default `0.25`) and `getLines`. Line `0` is
the top line. Empty strings are used as gaps.

### `NpcSkin` / `SkinFetcher`

`NpcSkin` holds the base64 texture value and its Mojang signature. `SkinFetcher.fetch(name)`
resolves a skin with blocking HTTP and caches it; `SkinFetcher.fetchAsync(plugin, name,
onSuccess, onFailure)` does the same off the main thread and calls back on the main thread.

## Notes and limitations

- **1.8.8 only.** The library talks to NMS `v1_8_R3` directly (with a tiny amount of
  reflection for packets whose fields are not public).
- **No interactions.** Because there is no server entity, the server never receives
  `use entity` packets for the NPC. If you need click detection, listen to
  `PacketPlayInUseEntity` (for example with ProtocolLib) and compare
  `packet.getEntityId()` with `Npc#getEntityId()`.
- **Name tags are profile names.** In 1.8 the client renders a player's name tag from its
  `GameProfile` name, so the vanilla tag is limited to 16 characters, has no colour codes
  and sanitises spaces to `_`. Use the hologram for formatted or longer names. The profile
  name follows `setName` automatically.
- **Tab list and skins.** The 1.8 client reads the skin from the player info entry, so the
  entry has to exist while the NPC spawns and is removed a moment later (default 20 ticks).
  If a player is too far away to have rendered the NPC before the entry is removed, their
  client falls back to the default skin. Raise `setTabListRemovalDelay(...)` or disable
  tab list hiding with `setHideFromTabList(false)` if this matters on your server.
- **No persistence.** NPCs live in memory; recreate them on enable (or store their data
  yourself).
- **Main thread only.** All API methods send packets and should be called from the server
  thread. `SkinFetcher.fetch` is the only blocking method, use `fetchAsync` on the server.
