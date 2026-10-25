package me.npclib;

/**
 * The mouse button that runs an {@link Npc#action(ClickType, java.util.function.Consumer)}
 * action.
 * <p>
 * The client sends a use-entity packet for both buttons, so the library can tell them apart
 * and run the action for the configured buttons only.
 */
public enum ClickType {

    /**
     * Right click (interact). The default for {@link Npc#action(java.util.function.Consumer)}.
     */
    RIGHT("right click"),

    /**
     * Left click (attack). The NPC is not a server entity, so the hit has no other effect.
     */
    LEFT("left click"),

    /**
     * Both mouse buttons.
     */
    BOTH("both clicks");

    private final String displayName;

    ClickType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return a human readable name, for example {@code "right click"}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param click the button that was pressed
     * @return {@code true} if an action with this type should run for that click
     */
    public boolean matches(ClickType click) {
        return this == BOTH || this == click;
    }

    @Override
    public String toString() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
