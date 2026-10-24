# NpcLib

A fully packet based NPC library for **Spigot 1.8.8**.

NPCs are never added to the server world. They only exist as packets, so they do not tick,
do not collide, cannot be pushed or killed and never despawn, while every player sees them
exactly like a normal player.

## Features

- Packet-only NPCs with a player skin (`GameProfile` textures), including the second skin layer
- Vanilla name tag toggle, implemented with a per-NPC scoreboard team
- Multi line hologram (invisible armour stands) above the NPC, shown instead of the name tag
- Equipment: hand, helmet, chestplate, leggings and boots
- `turnToPlayer` client side head and body rotation (with a configurable radius)
- Right-click actions: `npc.action(player -> ...)` runs your code when a player right-clicks the NPC
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

The resulting library jar is `target/NpcLib-0.0.5.jar`. It is not a standalone Bukkit plugin; add it as a dependency (or shade it into your plugin).

The build needs the `craftbukkit` 1.8.8 artifact, which is provided by the CodeMC NMS
repository (already configured in `pom.xml`). It is `provided`, so it is not shaded into
the jar.

## Using it as a library

NpcLib is a **library-only jar**; do not install it as a separate server plugin. Add the
artifact as a dependency or shade it into the plugin that owns the NPCs. Call
`NpcLib.init(this)` once from that plugin's `onEnable()` and `NpcLib.shutdown(this)` from
its `onDisable()`. Repeated `init(this)` calls are safe; a different plugin cannot silently
claim or shut down the active library instance.

```xml
<dependency>
    <groupId>me.npclib</groupId>
    <artifactId>npclib</artifactId>
    <version>0.0.5</version>
</dependency>
```

The dependency must be available to your plugin at runtime, or shaded into its jar. The
NpcLib project itself compiles against Spigot 1.8.8 with CraftBukkit marked `provided`.

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

        // Run code when a player right-clicks the NPC (main thread, consumer<Player>).
        npc.action(new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.sendMessage("§aWelcome to the bank!");
                player.openInventory(Bukkit.createInventory(null, 27, "§2Bank"));
            }
        });

        // Look at nearby players
        npc.setTurnToPlayer(true);
        npc.setLookRadius(32.0D);

        // Show the vanilla name tag instead of (or in addition to) the hologram
        npc.setNameVisible(true);
    }

    @Override
    public void onDisable() {
        NpcLib.shutdown(this);
    }
}
```

## API overview

### `NpcLib`

| Method | Description |
|---|---|
| `init(Plugin)` | Initialises the library, listeners and look task; repeated calls by the same plugin are safe |
| `createNpc(String name, Location location)` | Creates and shows an NPC |
| `getNpc(int entityId)` / `getNpc(UUID)` | Looks an NPC up |
| `getNpcs()` | All registered NPCs |
| `showAll(Player)` / `respawnAll(Player)` / `forget(Player)` | Lifecycle helpers |
| `setHideFromTabList(boolean)` | Enable/disable tab list hiding |
| `setTabListRemovalDelay(int ticks)` | Delay before the tab entry is removed (default 20) |
| `shutdown(Plugin)` | Removes every NPC and stops resources if called by the owning plugin |
| `shutdown()` | Forcefully shuts down the library (prefer the owner-specific overload) |

### `Npc`

| Method | Description |
|---|---|
| `setName(String)` | Display name, supports `&` colours |
| `setNameVisible(boolean)` | Toggles the vanilla name tag |
| `setSkin(NpcSkin)` / `setSkin(String value, String signature)` | Applies a skin |
| `setSkinLayer(SkinLayer, boolean)` | Shows/hides one second layer part (hat, jacket, sleeves, pant legs, cape) |
| `setSkinLayers(SkinLayer...)` | Replaces the visible second layer parts; no arguments hides all |
| `getSkinLayers()` / `hasSkinLayer(SkinLayer)` | The visible second layer parts |
| `setItem(NpcSlot, ItemStack)` | Equips an item (`null` clears the slot) |
| `action(Consumer<Player>)` | Runs when a player right-clicks the NPC (`null` removes it) |
| `getAction()` | The current click action, or `null` |
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

### `SkinLayer`

`CAPE`, `JACKET`, `LEFT_SLEEVE`, `RIGHT_SLEEVE`, `LEFT_PANT_LEG`, `RIGHT_PANT_LEG` and `HAT`.
Every second layer part is visible by default, matching the vanilla skin. An NPC only draws
parts that exist in its skin texture, so a classic skin still looks its usual self.

## Notes and limitations

- **1.8.8 only.** The library talks to NMS `v1_8_R3` directly (with a tiny amount of
  reflection for packets whose fields are not public).
- **Interactions.** Because there is no server entity, Bukkit never fires
  `PlayerInteractEntityEvent` for an NPC. NpcLib therefore hooks the incoming
  `PacketPlayInUseEntity` packet in each player's netty pipeline and runs
  `Npc#action(Consumer<Player>)` on the main thread for right clicks on an NPC the player
  can see. This needs no extra plugin (no ProtocolLib) and no server entity. Left clicks
  (attacks) are ignored; a client can only click an NPC that is within its reach anyway.
  `io.netty` is `provided` by the server, do not shade it into your plugin.
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
