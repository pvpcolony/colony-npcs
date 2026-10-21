package me.npclib.nms;

import com.mojang.authlib.GameProfile;
import me.npclib.NpcSlot;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntity.PacketPlayOutEntityLook;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PacketPlayOutScoreboardTeam;
import net.minecraft.server.v1_8_R3.PacketPlayOutSpawnEntityLiving;
import net.minecraft.server.v1_8_R3.Scoreboard;
import net.minecraft.server.v1_8_R3.ScoreboardTeam;
import net.minecraft.server.v1_8_R3.ScoreboardTeamBase;
import net.minecraft.server.v1_8_R3.WorldSettings;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Builds every packet needed by the library. Everything here is internal: use
 * {@link me.npclib.Npc} instead.
 */
public final class NpcPackets {

    private static final Constructor<?> PLAYER_INFO_DATA;
    private static final Field PLAYER_INFO_ACTION;
    private static final Field PLAYER_INFO_ENTRIES;

    private static final Field SPAWN_ENTITY_ID;
    private static final Field SPAWN_UUID;
    private static final Field SPAWN_X;
    private static final Field SPAWN_Y;
    private static final Field SPAWN_Z;
    private static final Field SPAWN_YAW;
    private static final Field SPAWN_PITCH;
    private static final Field SPAWN_ITEM;
    private static final Field SPAWN_DATA_WATCHER;

    private static final Field HEAD_ROTATION_ENTITY_ID;
    private static final Field HEAD_ROTATION_YAW;

    static {
        try {
            PLAYER_INFO_DATA = PacketPlayOutPlayerInfo.PlayerInfoData.class.getDeclaredConstructor(
                    PacketPlayOutPlayerInfo.class, GameProfile.class, int.class,
                    WorldSettings.EnumGamemode.class, IChatBaseComponent.class);
            PLAYER_INFO_DATA.setAccessible(true);
            PLAYER_INFO_ACTION = field(PacketPlayOutPlayerInfo.class, "a");
            PLAYER_INFO_ENTRIES = field(PacketPlayOutPlayerInfo.class, "b");

            SPAWN_ENTITY_ID = field(PacketPlayOutNamedEntitySpawn.class, "a");
            SPAWN_UUID = field(PacketPlayOutNamedEntitySpawn.class, "b");
            SPAWN_X = field(PacketPlayOutNamedEntitySpawn.class, "c");
            SPAWN_Y = field(PacketPlayOutNamedEntitySpawn.class, "d");
            SPAWN_Z = field(PacketPlayOutNamedEntitySpawn.class, "e");
            SPAWN_YAW = field(PacketPlayOutNamedEntitySpawn.class, "f");
            SPAWN_PITCH = field(PacketPlayOutNamedEntitySpawn.class, "g");
            SPAWN_ITEM = field(PacketPlayOutNamedEntitySpawn.class, "h");
            SPAWN_DATA_WATCHER = field(PacketPlayOutNamedEntitySpawn.class, "i");

            HEAD_ROTATION_ENTITY_ID = field(PacketPlayOutEntityHeadRotation.class, "a");
            HEAD_ROTATION_YAW = field(PacketPlayOutEntityHeadRotation.class, "b");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private NpcPackets() {
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    // ------------------------------------------------------------------------------------
    // player info / tab list
    // ------------------------------------------------------------------------------------

    public static PacketPlayOutPlayerInfo addPlayerInfo(NpcHandle npc) {
        return playerInfo(npc, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER);
    }

    public static PacketPlayOutPlayerInfo removePlayerInfo(NpcHandle npc) {
        return playerInfo(npc, PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER);
    }

    @SuppressWarnings("unchecked")
    private static PacketPlayOutPlayerInfo playerInfo(NpcHandle npc,
                                                      PacketPlayOutPlayerInfo.EnumPlayerInfoAction action) {
        PacketPlayOutPlayerInfo packet = new PacketPlayOutPlayerInfo();
        try {
            PLAYER_INFO_ACTION.set(packet, action);
            List<Object> entries = (List<Object>) PLAYER_INFO_ENTRIES.get(packet);
            entries.add(PLAYER_INFO_DATA.newInstance(packet, npc.getProfile(), 0,
                    WorldSettings.EnumGamemode.SURVIVAL, null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not build a player info packet", exception);
        }
        return packet;
    }

    // ------------------------------------------------------------------------------------
    // entity packets
    // ------------------------------------------------------------------------------------

    public static PacketPlayOutNamedEntitySpawn spawnNamed(NpcHandle npc) {
        PacketPlayOutNamedEntitySpawn packet = new PacketPlayOutNamedEntitySpawn();
        try {
            SPAWN_ENTITY_ID.setInt(packet, npc.getEntityId());
            SPAWN_UUID.set(packet, npc.getUniqueId());
            SPAWN_X.setInt(packet, fixed(npc.getX()));
            SPAWN_Y.setInt(packet, fixed(npc.getY()));
            SPAWN_Z.setInt(packet, fixed(npc.getZ()));
            SPAWN_YAW.setByte(packet, angle(npc.getYaw()));
            SPAWN_PITCH.setByte(packet, angle(npc.getPitch()));
            SPAWN_ITEM.setInt(packet, 0);
            SPAWN_DATA_WATCHER.set(packet, npc.getDataWatcher());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not build a named entity spawn packet", exception);
        }
        return packet;
    }

    public static PacketPlayOutEntityDestroy destroy(int... entityIds) {
        return new PacketPlayOutEntityDestroy(entityIds);
    }

    public static PacketPlayOutEntityMetadata metadata(NpcHandle npc) {
        return new PacketPlayOutEntityMetadata(npc.getEntityId(), npc.getDataWatcher(), true);
    }

    public static PacketPlayOutEntityEquipment equipment(int entityId, NpcSlot slot, ItemStack item) {
        return new PacketPlayOutEntityEquipment(entityId, slot.getProtocolId(), CraftItemStack.asNMSCopy(item));
    }

    public static PacketPlayOutSpawnEntityLiving spawnLiving(EntityLiving entity) {
        return new PacketPlayOutSpawnEntityLiving(entity);
    }

    public static PacketPlayOutEntityTeleport teleport(NpcHandle npc) {
        return new PacketPlayOutEntityTeleport(npc.getEntityId(), fixed(npc.getX()), fixed(npc.getY()),
                fixed(npc.getZ()), angle(npc.getYaw()), angle(npc.getPitch()), true);
    }

    public static PacketPlayOutEntityTeleport teleport(Entity entity) {
        return new PacketPlayOutEntityTeleport(entity.getId(), fixed(entity.locX), fixed(entity.locY),
                fixed(entity.locZ), angle(entity.yaw), angle(entity.pitch), true);
    }

    public static PacketPlayOutEntityHeadRotation headRotation(int entityId, float yaw) {
        PacketPlayOutEntityHeadRotation packet = new PacketPlayOutEntityHeadRotation();
        try {
            HEAD_ROTATION_ENTITY_ID.setInt(packet, entityId);
            HEAD_ROTATION_YAW.setByte(packet, angle(yaw));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not build a head rotation packet", exception);
        }
        return packet;
    }

    public static PacketPlayOutEntityLook look(int entityId, float yaw, float pitch) {
        return new PacketPlayOutEntityLook(entityId, angle(yaw), angle(pitch), true);
    }

    // ------------------------------------------------------------------------------------
    // scoreboard (name tag visibility)
    // ------------------------------------------------------------------------------------

    /**
     * Creates a client side scoreboard team. Its name tag visibility is what actually hides
     * or shows the name tag above a player NPC in 1.8.
     */
    public static ScoreboardTeam createTeam(String teamName, String entry, boolean nameVisible) {
        Scoreboard scoreboard = new Scoreboard();
        ScoreboardTeam team = scoreboard.createTeam(teamName);
        team.setDisplayName(teamName);
        team.setPrefix("");
        team.setSuffix("");
        team.setNameTagVisibility(nameVisible
                ? ScoreboardTeamBase.EnumNameTagVisibility.ALWAYS
                : ScoreboardTeamBase.EnumNameTagVisibility.NEVER);
        scoreboard.addPlayerToTeam(entry, teamName);
        return team;
    }

    public static void setNameTagVisible(ScoreboardTeam team, boolean visible) {
        team.setNameTagVisibility(visible
                ? ScoreboardTeamBase.EnumNameTagVisibility.ALWAYS
                : ScoreboardTeamBase.EnumNameTagVisibility.NEVER);
    }

    /** Mode 0: create the team and its entries. */
    public static PacketPlayOutScoreboardTeam teamCreate(ScoreboardTeam team) {
        return new PacketPlayOutScoreboardTeam(team, 0);
    }

    /** Mode 1: remove the team. */
    public static PacketPlayOutScoreboardTeam teamRemove(ScoreboardTeam team) {
        return new PacketPlayOutScoreboardTeam(team, 1);
    }

    /** Mode 2: update the team properties, including name tag visibility. */
    public static PacketPlayOutScoreboardTeam teamUpdate(ScoreboardTeam team) {
        return new PacketPlayOutScoreboardTeam(team, 2);
    }

    private static int fixed(double coordinate) {
        return (int) Math.floor(coordinate * 32.0D);
    }

    private static byte angle(float degrees) {
        return (byte) (degrees * 256.0F / 360.0F);
    }
}
