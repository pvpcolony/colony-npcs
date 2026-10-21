package me.npclib;

import org.bukkit.ChatColor;

/**
 * Small helper used to normalise user supplied text.
 */
final class Chat {

    private Chat() {
    }

    static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
