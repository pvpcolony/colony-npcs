package me.npclib.nms;

import com.mojang.authlib.GameProfile;
import me.npclib.SkinLayer;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.EntityArmorStand;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import java.util.UUID;

/**
 * All state needed to build NPC packets, without creating an actual server entity.
 * <p>
 * The entity id is taken from a throwaway armour stand so that it is guaranteed to be
 * unique on the server. The armour stand itself is never spawned and is garbage collected
 * right away.
 */
public final class NpcHandle {

    private final int entityId;
    private final UUID uniqueId;
    private GameProfile profile;
    private DataWatcher dataWatcher;
    private String customName = "";
    private byte skinFlags;

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    /**
     * Allocates a unique entity id and creates the handle for it.
     */
    public static NpcHandle create(World world, UUID uniqueId, GameProfile profile, Location location) {
        // The server side entity counter is private, so borrow its next id by creating a
        // throwaway armour stand that is never spawned or added to the world.
        int entityId = new EntityArmorStand(((CraftWorld) world).getHandle()).getId();
        return new NpcHandle(entityId, uniqueId, profile, location);
    }

    /**
     * Creates a handle for an entity id you manage yourself. The id must not be used by any
     * other entity, otherwise clients will mix the two entities up.
     */
    public NpcHandle(int entityId, UUID uniqueId, GameProfile profile, Location location) {
        this.entityId = entityId;
        this.uniqueId = uniqueId;
        this.profile = profile;
        this.skinFlags = SkinLayer.ALL;
        rebuildWatcher();

        setLocation(location);
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public GameProfile getProfile() {
        return profile;
    }

    /**
     * Replaces the profile, for example when the NPC name (and therefore the name tag)
     * changes. The UUID must stay the same.
     */
    public void setProfile(GameProfile profile) {
        this.profile = profile;
    }

    public DataWatcher getDataWatcher() {
        return dataWatcher;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setLocation(Location location) {
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Updates the custom name. A fresh data watcher is built because {@code DataWatcher#watch}
     * requires a server entity, and this NPC deliberately has none.
     */
    public void setCustomName(String name) {
        this.customName = name == null ? "" : name;
        rebuildWatcher();
    }

    /**
     * @return the skin parts byte sent to clients, see {@link SkinLayer}
     */
    public byte getSkinFlags() {
        return skinFlags;
    }

    /**
     * Sets which second skin layers the client draws, see {@link SkinLayer}.
     */
    public void setSkinFlags(byte skinFlags) {
        this.skinFlags = skinFlags;
        rebuildWatcher();
    }

    private void rebuildWatcher() {
        DataWatcher watcher = new DataWatcher(null);
        // Entity metadata indexes: 2 = custom name, 3 = custom name visible,
        // 10 = skin parts (0x01 cape, 0x02 jacket, 0x04 left sleeve, 0x08 right sleeve,
        // 0x10 left pant leg, 0x20 right pant leg, 0x40 hat). Leaving index 10 unset makes
        // the client hide every second skin layer, so it is always written explicitly.
        watcher.a(2, customName);
        watcher.a(3, (byte) 1);
        watcher.a(10, skinFlags);
        this.dataWatcher = watcher;
    }
}
