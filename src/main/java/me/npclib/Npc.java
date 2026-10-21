package me.npclib;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.npclib.nms.NpcHandle;
import me.npclib.nms.NpcPackets;
import net.minecraft.server.v1_8_R3.ScoreboardTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A completely packet based NPC.
 * <p>
 * The NPC is never added to the server world: it only exists as a small handle used to build
 * the spawn, metadata and equipment packets. That means it never ticks, never collides, never
 * despawns and is not attackable, while every player can still see it like a normal player.
 * <p>
 * Instances are created through {@link NpcLib#createNpc(String, Location)}.
 * All methods must be called from the main server thread.
 */
public final class Npc {

    private final UUID uniqueId;
    private final NpcHandle handle;
    private final Hologram hologram;
    private final Map<NpcSlot, ItemStack> equipment = new EnumMap<NpcSlot, ItemStack>(NpcSlot.class);
    private final Set<UUID> viewers = new HashSet<UUID>();

    private String profileName;
    private ScoreboardTeam team;
    private Location location;
    private String name;
    private boolean nameVisible = false;
    private boolean hologramShowsName = true;
    private boolean turnToPlayer = false;
    private double lookRadius = 32.0D;
    private boolean removed = false;

    Npc(String displayName, String profileName, Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("An NPC needs a location with a loaded world");
        }

        this.uniqueId = UUID.randomUUID();
        this.profileName = profileName;
        this.name = Chat.color(displayName);
        this.location = location.clone();

        GameProfile profile = new GameProfile(uniqueId, profileName);
        this.handle = NpcHandle.create(location.getWorld(), uniqueId, profile, location);
        this.handle.setCustomName(this.name);

        // 1.8 renders player name tags based on the scoreboard team, the custom name visible
        // metadata flag is ignored for players. NEVER hides the tag, ALWAYS shows it.
        this.team = NpcPackets.createTeam("npc" + handle.getEntityId(), profileName, false);

        this.hologram = new Hologram(this);
        this.hologram.setLinesInternal(Collections.singletonList(this.name));
    }

    // ------------------------------------------------------------------------------------
    // identity
    // ------------------------------------------------------------------------------------

    public UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * @return the internal profile name, also used as scoreboard team entry
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * @return the packet entity id, useful to identify the NPC in interaction packets
     */
    public int getEntityId() {
        return handle.getEntityId();
    }

    public Location getLocation() {
        return location.clone();
    }

    public boolean isRemoved() {
        return removed;
    }

    // ------------------------------------------------------------------------------------
    // name
    // ------------------------------------------------------------------------------------

    public String getName() {
        return name;
    }

    /**
     * Sets the display name. Supports {@code &} color codes. The name is used for the
     * vanilla name tag and, while the hologram is still "auto naming", for the hologram
     * as well.
     */
    public Npc setName(String name) {
        checkNotRemoved();
        this.name = Chat.color(name);
        this.handle.setCustomName(this.name);
        if (hologramShowsName) {
            hologram.setLinesInternal(Collections.singletonList(this.name));
        }

        // The 1.8 client takes the player name tag from the GameProfile name, so keep it in
        // sync with the display name (sanitised to a valid, unique profile name).
        String newProfileName = NpcLib.profileNameFor(this.name, this);
        if (!newProfileName.equals(this.profileName)) {
            applyProfileName(newProfileName);
        }

        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                NpcPackets.send(player, NpcPackets.metadata(handle));
            }
        }
        return this;
    }

    /**
     * Toggles the vanilla name tag above the NPC head. Since 1.8 renders player name tags
     * through the scoreboard, this is done with a per-NPC scoreboard team.
     */
    public boolean isNameVisible() {
        return nameVisible;
    }

    public Npc setNameVisible(boolean nameVisible) {
        checkNotRemoved();
        if (this.nameVisible != nameVisible) {
            this.nameVisible = nameVisible;
            NpcPackets.setNameTagVisible(team, nameVisible);
            for (UUID viewerId : viewerIds()) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null && player.isOnline()) {
                    NpcPackets.send(player, NpcPackets.teamUpdate(team));
                }
            }
        }
        return this;
    }

    // ------------------------------------------------------------------------------------
    // hologram
    // ------------------------------------------------------------------------------------

    public Hologram getHologram() {
        return hologram;
    }

    /**
     * Replaces the hologram lines and stops the hologram from following {@link #setName(String)}.
     */
    public Npc setHologramLines(String... lines) {
        hologramShowsName = false;
        hologram.setLines(lines);
        return this;
    }

    public Npc setHologramLines(List<String> lines) {
        hologramShowsName = false;
        hologram.setLines(lines);
        return this;
    }

    /**
     * Called by the hologram whenever lines are edited directly, so {@link #setName(String)}
     * stops overwriting them.
     */
    void markHologramManual() {
        this.hologramShowsName = false;
    }

    public Npc addHologramLine(String line) {
        hologramShowsName = false;
        hologram.addLine(line);
        return this;
    }

    public Npc setHologramVisible(boolean visible) {
        hologram.setVisible(visible);
        return this;
    }

    /**
     * @return {@code true} while the hologram still mirrors {@link #setName(String)}
     */
    public boolean isHologramTrackingName() {
        return hologramShowsName;
    }

    // ------------------------------------------------------------------------------------
    // skin
    // ------------------------------------------------------------------------------------

    /**
     * Applies a skin. Already spawned viewers get the NPC respawned with the new texture.
     *
     * @param skin the skin, or {@code null} for the default Steve/Alex skin
     */
    public Npc setSkin(NpcSkin skin) {
        checkNotRemoved();
        GameProfile profile = handle.getProfile();
        profile.getProperties().removeAll("textures");
        if (skin != null) {
            Property property = skin.hasSignature()
                    ? new Property("textures", skin.getValue(), skin.getSignature())
                    : new Property("textures", skin.getValue());
            profile.getProperties().put("textures", property);
        }

        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                refreshProfile(player);
            }
        }
        return this;
    }

    public Npc setSkin(String value, String signature) {
        return setSkin(new NpcSkin(value, signature));
    }

    /**
     * Applies a new GameProfile name, swaps the scoreboard team entry and respawns the NPC
     * for everyone, because the client caches the profile name of the entity.
     */
    private void applyProfileName(String newProfileName) {
        GameProfile oldProfile = handle.getProfile();
        GameProfile profile = new GameProfile(uniqueId, newProfileName);
        profile.getProperties().putAll(oldProfile.getProperties());
        handle.setProfile(profile);

        ScoreboardTeam oldTeam = team;
        this.profileName = newProfileName;
        this.team = NpcPackets.createTeam("npc" + handle.getEntityId(), newProfileName, nameVisible);

        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                NpcPackets.send(player, NpcPackets.teamRemove(oldTeam));
                NpcPackets.send(player, NpcPackets.teamCreate(team));
                refreshProfile(player);
            }
        }
    }

    private void refreshProfile(Player player) {
        NpcPackets.send(player, NpcPackets.removePlayerInfo(handle));
        NpcPackets.send(player, NpcPackets.destroy(handle.getEntityId()));
        NpcPackets.send(player, NpcPackets.addPlayerInfo(handle));
        NpcPackets.send(player, NpcPackets.spawnNamed(handle));
        sendEquipment(player);
        NpcPackets.send(player, NpcPackets.metadata(handle));
        scheduleTabListRemoval(player);
    }

    // ------------------------------------------------------------------------------------
    // equipment
    // ------------------------------------------------------------------------------------

    /**
     * Equips an item. Pass {@code null} to clear the slot.
     */
    public Npc setItem(NpcSlot slot, ItemStack item) {
        checkNotRemoved();
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        if (item == null) {
            equipment.remove(slot);
        } else {
            equipment.put(slot, item.clone());
        }
        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                NpcPackets.send(player, NpcPackets.equipment(handle.getEntityId(), slot, item));
            }
        }
        return this;
    }

    public ItemStack getItem(NpcSlot slot) {
        ItemStack item = equipment.get(slot);
        return item == null ? null : item.clone();
    }

    public Npc clearEquipment() {
        checkNotRemoved();
        for (NpcSlot slot : NpcSlot.values()) {
            equipment.remove(slot);
            for (UUID viewerId : viewerIds()) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null && player.isOnline()) {
                    NpcPackets.send(player, NpcPackets.equipment(handle.getEntityId(), slot, null));
                }
            }
        }
        return this;
    }

    private void sendEquipment(Player player) {
        for (NpcSlot slot : NpcSlot.values()) {
            NpcPackets.send(player, NpcPackets.equipment(handle.getEntityId(), slot, equipment.get(slot)));
        }
    }

    // ------------------------------------------------------------------------------------
    // movement / looking
    // ------------------------------------------------------------------------------------

    /**
     * Enables client side head and body rotation towards every player that can see the NPC.
     */
    public boolean isTurnToPlayer() {
        return turnToPlayer;
    }

    public Npc setTurnToPlayer(boolean turnToPlayer) {
        checkNotRemoved();
        this.turnToPlayer = turnToPlayer;
        return this;
    }

    public double getLookRadius() {
        return lookRadius;
    }

    public Npc setLookRadius(double lookRadius) {
        if (lookRadius <= 0.0D) {
            throw new IllegalArgumentException("Look radius must be positive");
        }
        this.lookRadius = lookRadius;
        return this;
    }

    public Npc teleport(Location destination) {
        checkNotRemoved();
        if (destination == null || destination.getWorld() == null) {
            throw new IllegalArgumentException("Destination needs a world");
        }

        boolean worldChanged = !destination.getWorld().equals(location.getWorld());
        if (worldChanged) {
            for (UUID viewerId : viewerIds()) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null && player.isOnline()) {
                    sendDespawn(player);
                }
            }
            viewers.clear();
        }

        this.location = destination.clone();
        applyLocation();

        if (worldChanged) {
            hologram.rebuild();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isInWorld(player.getWorld())) {
                    show(player);
                }
            }
        } else {
            for (UUID viewerId : viewerIds()) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null && player.isOnline()) {
                    NpcPackets.send(player, NpcPackets.teleport(handle));
                }
            }
            hologram.updatePositions();
        }
        return this;
    }

    /**
     * Pushes the current rotation to a single viewer. Called by the library task.
     */
    void tick() {
        if (!turnToPlayer || removed || viewers.isEmpty()) {
            return;
        }

        double radiusSquared = lookRadius * lookRadius;
        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline() || !player.getWorld().equals(location.getWorld())) {
                continue;
            }

            Location eye = player.getEyeLocation();
            double dx = eye.getX() - location.getX();
            double dy = eye.getY() - (location.getY() + 1.62D);
            double dz = eye.getZ() - location.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal * horizontal + dy * dy > radiusSquared) {
                continue;
            }

            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            pitch = Math.max(-90.0F, Math.min(90.0F, pitch));

            NpcPackets.send(player, NpcPackets.headRotation(handle.getEntityId(), yaw));
            NpcPackets.send(player, NpcPackets.look(handle.getEntityId(), yaw, pitch));

            handle.setRotation(yaw, pitch);
        }
    }

    // ------------------------------------------------------------------------------------
    // visibility
    // ------------------------------------------------------------------------------------

    public boolean isInWorld(World world) {
        return world != null && world.equals(location.getWorld());
    }

    /**
     * Shows the NPC to a player. Does nothing if the player is in another world or already
     * sees the NPC.
     *
     * @return {@code true} if the NPC was spawned for the player
     */
    public boolean show(Player player) {
        if (removed || player == null || !player.isOnline() || !isInWorld(player.getWorld())) {
            return false;
        }
        if (!viewers.add(player.getUniqueId())) {
            return false;
        }
        sendSpawn(player);
        return true;
    }

    /**
     * Removes the NPC from a player's client.
     *
     * @return {@code true} if the player was viewing the NPC
     */
    public boolean hide(Player player) {
        if (player == null || !viewers.remove(player.getUniqueId())) {
            return false;
        }
        if (player.isOnline()) {
            sendDespawn(player);
        }
        return true;
    }

    public boolean isShownTo(Player player) {
        return player != null && viewers.contains(player.getUniqueId());
    }

    public Collection<UUID> getViewerIds() {
        return Collections.unmodifiableSet(new HashSet<UUID>(viewers));
    }

    /**
     * Removes the NPC for every player and unregisters it.
     */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (UUID viewerId : viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                sendDespawn(player);
            }
        }
        viewers.clear();
        NpcLib.unregister(this);
    }

    // ------------------------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------------------------

    void spawnForAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
    }

    /**
     * Re-sends the spawn packets without tracking changes, used after respawn or world
     * changes where the client already wiped its entity list.
     */
    void respawnFor(Player player) {
        if (removed || player == null || !player.isOnline() || !isInWorld(player.getWorld())) {
            return;
        }
        viewers.add(player.getUniqueId());
        sendSpawn(player);
    }

    void forgetViewer(Player player) {
        if (player != null) {
            viewers.remove(player.getUniqueId());
        }
    }

    Set<UUID> viewerIds() {
        return new HashSet<UUID>(viewers);
    }

    private void sendSpawn(Player player) {
        // Create the team first so the name tag never flashes for a single frame.
        NpcPackets.send(player, NpcPackets.teamCreate(team));
        NpcPackets.send(player, NpcPackets.addPlayerInfo(handle));
        NpcPackets.send(player, NpcPackets.spawnNamed(handle));
        sendEquipment(player);
        NpcPackets.send(player, NpcPackets.metadata(handle));
        hologram.showTo(player);
        scheduleTabListRemoval(player);
    }

    private void sendDespawn(Player player) {
        NpcPackets.send(player, NpcPackets.destroy(handle.getEntityId()));
        hologram.hideFrom(player);
        NpcPackets.send(player, NpcPackets.teamRemove(team));
        NpcPackets.send(player, NpcPackets.removePlayerInfo(handle));
    }

    private void scheduleTabListRemoval(final Player player) {
        if (!NpcLib.isHideFromTabList()) {
            return;
        }
        final UUID viewerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(NpcLib.getPlugin(), new Runnable() {
            @Override
            public void run() {
                if (!viewers.contains(viewerId)) {
                    return;
                }
                Player online = Bukkit.getPlayer(viewerId);
                if (online != null && online.isOnline()) {
                    NpcPackets.send(online, NpcPackets.removePlayerInfo(handle));
                }
            }
        }, NpcLib.getTabListRemovalDelay());
    }

    private void applyLocation() {
        handle.setLocation(location);
    }

    private void checkNotRemoved() {
        if (removed) {
            throw new IllegalStateException("This NPC has been removed");
        }
    }

    @Override
    public String toString() {
        return "Npc{name=" + name + ", entityId=" + handle.getEntityId() + ", profile=" + profileName + '}';
    }
}
