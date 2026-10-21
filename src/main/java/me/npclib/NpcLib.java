package me.npclib;

import me.npclib.listener.NpcListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point of the library.
 * <p>
 * Call {@link #init(Plugin)} once (this registers the join/quit listeners and the look
 * task), then create NPCs with {@link #createNpc(String, Location)}.
 * All methods must be called from the main server thread.
 */
public final class NpcLib {

    private static final Map<UUID, Npc> NPCS = new LinkedHashMap<UUID, Npc>();
    private static final Map<Integer, Npc> NPCS_BY_ENTITY_ID = new HashMap<Integer, Npc>();
    /**
     * Entity ids that belong to an NPC. Read from the netty thread to skip scheduling a
     * task for every interaction packet with a normal entity.
     */
    private static final Set<Integer> INTERACTIVE_ENTITY_IDS =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private static volatile Plugin plugin;
    private static NpcListener listener;
    private static BukkitTask lookTask;
    private static boolean hideFromTabList = true;
    private static int tabListRemovalDelay = 20;

    private NpcLib() {
    }

    /**
     * Initialises the library for the given plugin. Registers the player listeners and
     * starts the task that rotates look-at-player NPCs. Calling this repeatedly with the
     * same plugin is safe and does not register duplicate listeners or tasks.
     *
     * @throws IllegalStateException if another plugin already owns this library instance
     */
    public static synchronized void init(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        if (NpcLib.plugin == plugin) {
            return;
        }
        if (NpcLib.plugin != null) {
            throw new IllegalStateException("NpcLib is already initialised by plugin '"
                    + NpcLib.plugin.getName() + "'; it cannot also be owned by '" + plugin.getName() + "'");
        }

        NpcLib.plugin = plugin;
        NpcLib.listener = new NpcListener();
        try {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
            // Hook players that are already online; later joins are handled by the listener.
            for (Player player : Bukkit.getOnlinePlayers()) {
                InteractionInjector.inject(player);
            }
            lookTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    for (Npc npc : new ArrayList<Npc>(NPCS.values())) {
                        npc.tick();
                    }
                }
            }, 2L, 2L);
        } catch (RuntimeException exception) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
            NpcLib.plugin = null;
            throw exception;
        }
    }

    public static boolean isInitialised() {
        return plugin != null;
    }

    public static Plugin getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("NpcLib.init(plugin) was not called");
        }
        return plugin;
    }

    /**
     * Creates an NPC at the given location and shows it to every online player in that world.
     *
     * @param name     the display name, supports {@code &} color codes
     * @param location where the NPC should stand
     * @return the created NPC
     */
    public static Npc createNpc(String name, Location location) {
        getPlugin();
        String profileName = profileNameFor(name, null);
        Npc npc = new Npc(name, profileName, location);

        NPCS.put(npc.getUniqueId(), npc);
        NPCS_BY_ENTITY_ID.put(npc.getEntityId(), npc);
        INTERACTIVE_ENTITY_IDS.add(npc.getEntityId());

        npc.spawnForAll();
        return npc;
    }

    public static Npc getNpc(UUID uniqueId) {
        return uniqueId == null ? null : NPCS.get(uniqueId);
    }

    public static Npc getNpc(int entityId) {
        return NPCS_BY_ENTITY_ID.get(entityId);
    }

    public static Collection<Npc> getNpcs() {
        return Collections.unmodifiableCollection(new ArrayList<Npc>(NPCS.values()));
    }

    /**
     * Shows every NPC that belongs to the player's world and hides the rest.
     */
    public static void showAll(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        InteractionInjector.inject(player);
        for (Npc npc : new ArrayList<Npc>(NPCS.values())) {
            if (npc.isInWorld(player.getWorld())) {
                npc.show(player);
            } else {
                // The client wiped the entities of the old world, no packets needed and the
                // client scoreboard was reset as well.
                npc.forgetViewer(player);
            }
        }
    }

    /**
     * Re-sends every visible NPC after the client wiped its entity list (respawn, dimension
     * change). Safe to call repeatedly.
     */
    public static void respawnAll(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        InteractionInjector.inject(player);
        for (Npc npc : new ArrayList<Npc>(NPCS.values())) {
            if (npc.isInWorld(player.getWorld())) {
                npc.respawnFor(player);
            } else {
                npc.forgetViewer(player);
            }
        }
    }

    /**
     * Forgets that the player is viewing any NPC. Used on quit, no packets are sent.
     */
    public static void forget(Player player) {
        if (player == null) {
            return;
        }
        InteractionInjector.uninject(player);
        for (Npc npc : new ArrayList<Npc>(NPCS.values())) {
            npc.forgetViewer(player);
        }
    }

    /**
     * Removes every NPC and stops library resources only when called by the owning plugin.
     * Use this overload from a plugin's {@code onDisable()} to prevent a different plugin
     * from shutting down the active library instance.
     *
     * @param owner the plugin that called {@link #init(Plugin)}
     * @return {@code true} if the library was shut down, otherwise {@code false}
     */
    public static synchronized boolean shutdown(Plugin owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner cannot be null");
        }
        if (plugin != owner) {
            return false;
        }
        shutdownInternal();
        return true;
    }

    /**
     * Forcefully removes every NPC and stops all library resources. Prefer
     * {@link #shutdown(Plugin)} in plugin lifecycle callbacks so another plugin cannot
     * accidentally shut down the active library instance.
     */
    public static synchronized void shutdown() {
        shutdownInternal();
    }

    private static void shutdownInternal() {
        Plugin owner = plugin;
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        for (Npc npc : new ArrayList<Npc>(NPCS.values())) {
            npc.remove();
        }
        NPCS.clear();
        NPCS_BY_ENTITY_ID.clear();
        INTERACTIVE_ENTITY_IDS.clear();
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        plugin = null;
    }

    static void unregister(Npc npc) {
        NPCS.remove(npc.getUniqueId());
        NPCS_BY_ENTITY_ID.remove(npc.getEntityId());
        INTERACTIVE_ENTITY_IDS.remove(npc.getEntityId());
    }

    // ------------------------------------------------------------------------------------
    // interactions
    // ------------------------------------------------------------------------------------

    /**
     * Called on the netty thread for every use-entity packet that targets an NPC.
     */
    static void handleInteraction(final UUID playerId, final int entityId) {
        final Plugin owner = plugin;
        if (owner == null) {
            return;
        }

        Bukkit.getScheduler().runTask(owner, new Runnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }
                Npc npc = NPCS_BY_ENTITY_ID.get(entityId);
                if (npc == null || !npc.isShownTo(player)) {
                    return;
                }
                npc.runAction(player);
            }
        });
    }

    /**
     * @return whether the entity id belongs to an NPC, safe to call from any thread
     */
    static boolean isInteractiveEntity(int entityId) {
        return INTERACTIVE_ENTITY_IDS.contains(entityId);
    }

    static void reportError(String message, Throwable throwable) {
        Plugin owner = plugin;
        if (owner != null) {
            owner.getLogger().log(java.util.logging.Level.SEVERE, message, throwable);
        } else {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE, message, throwable);
        }
    }

    /**
     * Builds a valid, unique GameProfile name from a display name. Player name tags in 1.8
     * display the profile name, which can not contain spaces or colour codes and is limited
     * to 16 characters, so the hologram is the place for formatted names.
     */
    static String profileNameFor(String displayName, Npc exclude) {
        String base = sanitize(ChatColor.stripColor(Chat.color(displayName)));
        if (base.isEmpty()) {
            base = "NPC";
        }
        if (base.length() > 16) {
            base = base.substring(0, 16);
        }

        String candidate = base;
        int suffix = 2;
        while (isProfileNameTaken(candidate, exclude)) {
            String number = String.valueOf(suffix++);
            int keep = Math.max(1, 16 - number.length());
            candidate = (base.length() > keep ? base.substring(0, keep) : base) + number;
        }
        return candidate;
    }

    private static boolean isProfileNameTaken(String name, Npc exclude) {
        for (Npc npc : NPCS.values()) {
            if (npc != exclude && npc.getProfileName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String input) {
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_') {
                builder.append(character);
            } else if (character == ' ') {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    /**
     * @return whether NPC entries are removed from the tab list
     */
    public static boolean isHideFromTabList() {
        return hideFromTabList;
    }

    /**
     * Toggles hiding NPC profile entries from the tab list.
     * <p>
     * The 1.8 client takes the skin from the player info entry, so the entry has to exist
     * while the NPC spawns and is only removed a moment later. If your players report
     * default skins on NPCs that are far away, disable this or raise the delay.
     */
    public static void setHideFromTabList(boolean hideFromTabList) {
        NpcLib.hideFromTabList = hideFromTabList;
    }

    /**
     * @return ticks between spawning an NPC and removing its tab list entry
     */
    public static int getTabListRemovalDelay() {
        return tabListRemovalDelay;
    }

    /**
     * Sets how long the fake player info entry stays in the tab list before it is removed.
     * Defaults to 20 ticks and must be at least 1.
     */
    public static void setTabListRemovalDelay(int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Delay must be at least 1 tick");
        }
        NpcLib.tabListRemovalDelay = ticks;
    }
}
