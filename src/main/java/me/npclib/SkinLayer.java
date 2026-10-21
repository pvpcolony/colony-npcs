package me.npclib;

import java.util.Collection;

/**
 * One part of the second ("overlay") skin layer, the same parts the vanilla skin customisation
 * menu toggles: hat, jacket, sleeves, pant legs and cape.
 * <p>
 * All parts are visible by default. Toggle them with {@link Npc#setSkinLayer(SkinLayer, boolean)}
 * or replace the whole set with {@link Npc#setSkinLayers(SkinLayer...)}.
 */
public enum SkinLayer {

    CAPE("cape", 0x01),
    JACKET("jacket", 0x02),
    LEFT_SLEEVE("left sleeve", 0x04),
    RIGHT_SLEEVE("right sleeve", 0x08),
    LEFT_PANT_LEG("left pant leg", 0x10),
    RIGHT_PANT_LEG("right pant leg", 0x20),
    HAT("hat", 0x40);

    /**
     * Flag byte with every second layer visible.
     */
    public static final byte ALL = 0x7F;

    /**
     * Flag byte with no second layer visible.
     */
    public static final byte NONE = 0x00;

    private final String displayName;
    private final int mask;

    SkinLayer(String displayName, int mask) {
        this.displayName = displayName;
        this.mask = mask;
    }

    /**
     * @return the bit that represents this layer in the skin parts metadata
     */
    public int getMask() {
        return mask;
    }

    /**
     * @return a human readable name, for example {@code "right sleeve"}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return {@code true} if the given metadata flags contain this layer
     */
    public boolean isVisible(int flags) {
        return (flags & mask) != 0;
    }

    /**
     * Packs layers into the byte the client expects in the skin parts metadata.
     */
    public static byte toFlags(Collection<SkinLayer> layers) {
        int flags = 0;
        if (layers != null) {
            for (SkinLayer layer : layers) {
                if (layer != null) {
                    flags |= layer.mask;
                }
            }
        }
        return (byte) flags;
    }

    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
