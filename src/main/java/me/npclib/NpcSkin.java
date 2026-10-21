package me.npclib;

/**
 * A Minecraft skin, made of the base64 encoded texture value and its Mojang signature.
 * <p>
 * You can obtain one with {@link me.npclib.skin.SkinFetcher#fetch(String)} (blocking) or
 * {@link me.npclib.skin.SkinFetcher#fetchAsync(org.bukkit.plugin.Plugin, String, java.util.function.Consumer, java.util.function.Consumer)}
 * and pass it to {@link Npc#setSkin(NpcSkin)}.
 */
public final class NpcSkin {

    private final String value;
    private final String signature;

    public NpcSkin(String value, String signature) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Skin value cannot be empty");
        }
        this.value = value;
        this.signature = signature;
    }

    /**
     * @return the base64 encoded texture value
     */
    public String getValue() {
        return value;
    }

    /**
     * @return the Mojang signature, may be {@code null} for unsigned skins
     */
    public String getSignature() {
        return signature;
    }

    public boolean hasSignature() {
        return signature != null && !signature.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NpcSkin)) {
            return false;
        }
        NpcSkin other = (NpcSkin) o;
        return value.equals(other.value)
                && (signature == null ? other.signature == null : signature.equals(other.signature));
    }

    @Override
    public int hashCode() {
        return 31 * value.hashCode() + (signature == null ? 0 : signature.hashCode());
    }

    @Override
    public String toString() {
        return "NpcSkin{value=" + (value.length() > 24 ? value.substring(0, 24) + "..." : value)
                + ", signed=" + hasSignature() + '}';
    }
}
