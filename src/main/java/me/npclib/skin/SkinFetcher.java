package me.npclib.skin;

import me.npclib.NpcSkin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the skin of a Minecraft account through the public Mojang APIs.
 * <p>
 * Results are cached in memory, so looking up the same player twice only costs one request.
 * Always use the asynchronous variant on the main thread.
 */
public final class SkinFetcher {

    private static final String PROFILE_BY_NAME = "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String PROFILE_BY_ID = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";
    private static final int TIMEOUT_MILLIS = 10_000;

    private static final ConcurrentMap<String, NpcSkin> CACHE = new ConcurrentHashMap<String, NpcSkin>();

    private SkinFetcher() {
    }

    /**
     * Looks the skin of a player up. This performs blocking HTTP requests and must never be
     * called from the main server thread.
     *
     * @param playerName the exact Minecraft name
     * @return the skin, or {@code null} if the account or its skin could not be found
     * @throws IOException if the Mojang API could not be reached
     */
    public static NpcSkin fetch(String playerName) throws IOException {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be empty");
        }

        String cacheKey = playerName.toLowerCase(Locale.ROOT);
        NpcSkin cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String lookup = read(PROFILE_BY_NAME, playerName);
        if (lookup == null) {
            return null;
        }

        String id = extract(lookup, "id");
        if (id == null) {
            return null;
        }

        String profile = read(PROFILE_BY_ID, id);
        if (profile == null) {
            return null;
        }

        String value = extract(profile, "value");
        if (value == null) {
            return null;
        }

        NpcSkin skin = new NpcSkin(value, extract(profile, "signature"));
        CACHE.put(cacheKey, skin);
        return skin;
    }

    /**
     * Resolves a skin off the main thread and hands the result back on the main thread.
     *
     * @param plugin    the owning plugin, used for scheduling
     * @param name      the Minecraft name to look up
     * @param onSuccess called on the main thread with the skin
     * @param onFailure called on the main thread when the lookup fails
     */
    public static void fetchAsync(final Plugin plugin, final String name,
                                  final Consumer<NpcSkin> onSuccess, final Consumer<IOException> onFailure) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    final NpcSkin skin = fetch(name);
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            onSuccess.accept(skin);
                        }
                    });
                } catch (final IOException exception) {
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (onFailure != null) {
                                onFailure.accept(exception);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Drops the cache entry of a player, so the next lookup goes back to Mojang.
     */
    public static void invalidate(String playerName) {
        if (playerName != null) {
            CACHE.remove(playerName.toLowerCase(Locale.ROOT));
        }
    }

    private static String read(String endpoint, String argument) throws IOException {
        String encoded = URLEncoder.encode(argument, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(String.format(endpoint, encoded)).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "NpcLib/1.0 (Spigot 1.8)");
        connection.setRequestProperty("Accept", "application/json");

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_FOUND) {
            return null;
        }
        if (status != HttpURLConnection.HTTP_OK) {
            throw new IOException("Mojang API returned HTTP " + status);
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return builder.toString();
    }

    private static String extract(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
