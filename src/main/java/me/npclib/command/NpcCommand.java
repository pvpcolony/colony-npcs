package me.npclib.command;

import me.npclib.Npc;
import me.npclib.NpcLib;
import me.npclib.NpcSkin;
import me.npclib.NpcSlot;
import me.npclib.skin.SkinFetcher;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Demo and administration command. Every feature of the library can be reached from here.
 */
public final class NpcCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[NpcLib] " + ChatColor.GRAY;
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "remove", "list", "name", "nametoggle", "skin", "me", "holo",
            "addline", "line", "removeline", "holotoggle", "item", "look", "here", "id", "help");

    private final Plugin plugin;

    public NpcCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("create")) {
            String name = args.length > 1 ? join(args, 1) : "&bNPC";
            Npc npc = NpcLib.createNpc(name, player.getLocation());
            player.sendMessage(PREFIX + "Created " + ChatColor.WHITE + npc.getProfileName()
                    + ChatColor.GRAY + " (entity id " + npc.getEntityId() + ").");

            SkinFetcher.fetchAsync(plugin, player.getName(), new java.util.function.Consumer<NpcSkin>() {
                @Override
                public void accept(NpcSkin skin) {
                    if (skin != null && !npc.isRemoved()) {
                        npc.setSkin(skin);
                    }
                }
            }, null);
            return true;
        }

        if (sub.equals("list")) {
            if (NpcLib.getNpcs().isEmpty()) {
                player.sendMessage(PREFIX + "There are no NPCs.");
                return true;
            }
            for (Npc npc : NpcLib.getNpcs()) {
                player.sendMessage(PREFIX + ChatColor.WHITE + npc.getProfileName()
                        + ChatColor.GRAY + " | id " + npc.getEntityId()
                        + " | " + npc.getLocation().getWorld().getName()
                        + " " + Math.round(npc.getLocation().getX())
                        + ", " + Math.round(npc.getLocation().getY())
                        + ", " + Math.round(npc.getLocation().getZ())
                        + " | " + ChatColor.stripColor(npc.getName()));
            }
            return true;
        }

        Npc npc = nearest(player);
        if (npc == null) {
            player.sendMessage(PREFIX + "No NPC within 16 blocks. Use /" + label + " create first.");
            return true;
        }

        switch (sub) {
            case "remove":
                npc.remove();
                player.sendMessage(PREFIX + "NPC removed.");
                return true;

            case "name":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " name <text...>");
                    return true;
                }
                npc.setName(join(args, 1));
                player.sendMessage(PREFIX + "Name updated.");
                return true;

            case "nametoggle":
                npc.setNameVisible(!npc.isNameVisible());
                player.sendMessage(PREFIX + "Vanilla name tag " + (npc.isNameVisible() ? "shown" : "hidden") + ".");
                return true;

            case "skin":
            case "me": {
                String target = sub.equals("me") ? player.getName()
                        : (args.length > 1 ? args[1] : player.getName());
                player.sendMessage(PREFIX + "Looking up the skin of " + target + "...");
                SkinFetcher.fetchAsync(plugin, target, new java.util.function.Consumer<NpcSkin>() {
                    @Override
                    public void accept(NpcSkin skin) {
                        if (npc.isRemoved()) {
                            return;
                        }
                        if (skin == null) {
                            player.sendMessage(PREFIX + "No skin found for " + target + ".");
                            return;
                        }
                        npc.setSkin(skin);
                        player.sendMessage(PREFIX + "Skin applied.");
                    }
                }, new java.util.function.Consumer<IOException>() {
                    @Override
                    public void accept(IOException exception) {
                        player.sendMessage(PREFIX + "Skin lookup failed: " + exception.getMessage());
                    }
                });
                return true;
            }

            case "holo":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " holo <text...>");
                    return true;
                }
                npc.setHologramLines(join(args, 1));
                player.sendMessage(PREFIX + "Hologram set to one line.");
                return true;

            case "addline":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " addline <text...>");
                    return true;
                }
                npc.addHologramLine(join(args, 1));
                player.sendMessage(PREFIX + "Line added. Total: " + npc.getHologram().getLines().size());
                return true;

            case "line":
                if (args.length < 3) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " line <index> <text...>");
                    return true;
                }
                try {
                    npc.getHologram().setLine(Integer.parseInt(args[1]), join(args, 2));
                    player.sendMessage(PREFIX + "Line updated.");
                } catch (RuntimeException exception) {
                    player.sendMessage(PREFIX + "Invalid line index.");
                }
                return true;

            case "removeline":
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " removeline <index>");
                    return true;
                }
                try {
                    npc.getHologram().removeLine(Integer.parseInt(args[1]));
                    player.sendMessage(PREFIX + "Line removed.");
                } catch (RuntimeException exception) {
                    player.sendMessage(PREFIX + "Invalid line index.");
                }
                return true;

            case "holotoggle":
                npc.setHologramVisible(!npc.getHologram().isVisible());
                player.sendMessage(PREFIX + "Hologram " + (npc.getHologram().isVisible() ? "shown" : "hidden") + ".");
                return true;

            case "item": {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "Usage: /" + label + " item <hand|helmet|chestplate|leggings|boots>");
                    return true;
                }
                NpcSlot slot;
                try {
                    slot = NpcSlot.valueOf(args[1].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(PREFIX + "Unknown slot. Use hand, helmet, chestplate, leggings or boots.");
                    return true;
                }
                ItemStack item = player.getItemInHand();
                npc.setItem(slot, item == null || item.getType() == Material.AIR ? null : item);
                player.sendMessage(PREFIX + "Equipped " + args[1].toLowerCase(Locale.ROOT) + ".");
                return true;
            }

            case "look":
                npc.setTurnToPlayer(!npc.isTurnToPlayer());
                player.sendMessage(PREFIX + "Turn to player " + (npc.isTurnToPlayer() ? "enabled" : "disabled") + ".");
                return true;

            case "here":
                npc.teleport(player.getLocation());
                player.sendMessage(PREFIX + "NPC moved to you.");
                return true;

            case "id":
                player.sendMessage(PREFIX + "Entity id: " + npc.getEntityId()
                        + ", profile: " + npc.getProfileName()
                        + ", viewers: " + npc.getViewerIds().size());
                return true;

            default:
                sendHelp(player, label);
                return true;
        }
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(ChatColor.DARK_AQUA + "NpcLib commands:");
        player.sendMessage(ChatColor.GRAY + "/" + label + " create [name...]" + ChatColor.DARK_GRAY + " - spawn an NPC");
        player.sendMessage(ChatColor.GRAY + "/" + label + " remove" + ChatColor.DARK_GRAY + " - remove the nearest NPC");
        player.sendMessage(ChatColor.GRAY + "/" + label + " name <text...>" + ChatColor.DARK_GRAY + " - set the name");
        player.sendMessage(ChatColor.GRAY + "/" + label + " nametoggle" + ChatColor.DARK_GRAY + " - toggle the vanilla name tag");
        player.sendMessage(ChatColor.GRAY + "/" + label + " skin <player>" + ChatColor.DARK_GRAY + " - apply a Mojang skin");
        player.sendMessage(ChatColor.GRAY + "/" + label + " holo <text...>" + ChatColor.DARK_GRAY + " - replace the hologram");
        player.sendMessage(ChatColor.GRAY + "/" + label + " addline <text...>" + ChatColor.DARK_GRAY + " - add a hologram line");
        player.sendMessage(ChatColor.GRAY + "/" + label + " line <index> <text...>" + ChatColor.DARK_GRAY + " - edit a line");
        player.sendMessage(ChatColor.GRAY + "/" + label + " removeline <index>" + ChatColor.DARK_GRAY + " - remove a line");
        player.sendMessage(ChatColor.GRAY + "/" + label + " holotoggle" + ChatColor.DARK_GRAY + " - show/hide the hologram");
        player.sendMessage(ChatColor.GRAY + "/" + label + " item <slot>" + ChatColor.DARK_GRAY + " - equip the held item");
        player.sendMessage(ChatColor.GRAY + "/" + label + " look" + ChatColor.DARK_GRAY + " - toggle looking at players");
        player.sendMessage(ChatColor.GRAY + "/" + label + " here" + ChatColor.DARK_GRAY + " - move the NPC to you");
        player.sendMessage(ChatColor.GRAY + "/" + label + " list" + ChatColor.DARK_GRAY + " - list all NPCs");
    }

    private Npc nearest(Player player) {
        Npc nearest = null;
        double best = 16.0D * 16.0D;
        for (Npc npc : NpcLib.getNpcs()) {
            if (!npc.isInWorld(player.getWorld())) {
                continue;
            }
            double distance = npc.getLocation().distanceSquared(player.getLocation());
            if (distance <= best) {
                best = distance;
                nearest = npc;
            }
        }
        return nearest;
    }

    private static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            List<String> slots = new ArrayList<String>();
            for (NpcSlot slot : NpcSlot.values()) {
                slots.add(slot.name().toLowerCase(Locale.ROOT));
            }
            return filter(slots, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
            List<String> names = new ArrayList<String>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<String>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
