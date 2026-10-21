package me.npclib;

import me.npclib.nms.NpcPackets;
import net.minecraft.server.v1_8_R3.EntityArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A floating, multi line text display made of invisible and small armour stands.
 * <p>
 * Every line is its own packet-only entity, so the hologram never exists in the world and
 * can not be touched, pushed or killed by players. Line {@code 0} is the top line.
 * The whole hologram is positioned relative to the owning NPC, so it follows teleports.
 */
public final class Hologram {

    /** Distance of the name tag above the armour stand, because they are small. */
    private static final double LABEL_OFFSET = 0.5D;

    private final Npc npc;
    private final List<String> lines = new ArrayList<String>();
    private final List<Line> rendered = new ArrayList<Line>();

    private double offset = 2.0D;
    private double lineSpacing = 0.25D;
    private boolean visible = true;

    Hologram(Npc npc) {
        this.npc = npc;
    }

    /**
     * Replaces every line. Colors may be provided with {@code &} codes.
     */
    public Hologram setLines(String... newLines) {
        return setLines(newLines == null ? Collections.<String>emptyList() : Arrays.asList(newLines));
    }

    /**
     * Replaces every line. Colors may be provided with {@code &} codes.
     */
    public Hologram setLines(List<String> newLines) {
        List<String> colored = new ArrayList<String>();
        if (newLines != null) {
            for (String line : newLines) {
                colored.add(Chat.color(line));
            }
        }
        npc.markHologramManual();
        applyLines(colored);
        return this;
    }

    /**
     * Adds a line at the bottom of the hologram.
     */
    public Hologram addLine(String line) {
        npc.markHologramManual();
        lines.add(Chat.color(line));
        rebuild();
        return this;
    }

    /**
     * Inserts a line at the given index, {@code 0} being the top line.
     */
    public Hologram insertLine(int index, String line) {
        if (index < 0 || index > lines.size()) {
            throw new IndexOutOfBoundsException("index " + index + " is outside 0.." + lines.size());
        }
        npc.markHologramManual();
        lines.add(index, Chat.color(line));
        rebuild();
        return this;
    }

    /**
     * Replaces a single line.
     */
    public Hologram setLine(int index, String line) {
        npc.markHologramManual();
        lines.set(index, Chat.color(line));
        rebuild();
        return this;
    }

    /**
     * Removes a single line.
     */
    public Hologram removeLine(int index) {
        npc.markHologramManual();
        lines.remove(index);
        if (lines.isEmpty()) {
            lines.add("");
        }
        rebuild();
        return this;
    }

    /**
     * Removes every line. An empty hologram is not shown.
     */
    public Hologram clearLines() {
        npc.markHologramManual();
        lines.clear();
        lines.add("");
        rebuild();
        return this;
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Hides or shows the whole hologram without losing its lines.
     */
    public Hologram setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            rebuild();
        }
        return this;
    }

    /**
     * @return how high the bottom line floats above the NPC's feet, in blocks
     */
    public double getOffset() {
        return offset;
    }

    public Hologram setOffset(double offset) {
        this.offset = offset;
        rebuild();
        return this;
    }

    /**
     * @return the vertical distance between two lines, in blocks
     */
    public double getLineSpacing() {
        return lineSpacing;
    }

    public Hologram setLineSpacing(double lineSpacing) {
        if (lineSpacing <= 0.0D) {
            throw new IllegalArgumentException("Line spacing must be positive");
        }
        this.lineSpacing = lineSpacing;
        rebuild();
        return this;
    }

    // ------------------------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------------------------

    /**
     * Used by {@link Npc#setName(String)} so the name shown on the hologram follows the NPC
     * name without switching the hologram into "manual lines" mode.
     */
    void setLinesInternal(List<String> newLines) {
        List<String> plain = new ArrayList<String>();
        for (String line : newLines) {
            plain.add(line == null ? "" : line);
        }
        applyLines(plain);
    }

    private void applyLines(List<String> newLines) {
        lines.clear();
        if (newLines.isEmpty()) {
            lines.add("");
        } else {
            lines.addAll(newLines);
        }
        rebuild();
    }

    void showTo(Player player) {
        for (Line line : rendered) {
            NpcPackets.send(player, NpcPackets.spawnLiving(line.stand));
        }
    }

    void hideFrom(Player player) {
        if (!rendered.isEmpty()) {
            NpcPackets.send(player, NpcPackets.destroy(ids()));
        }
    }

    /**
     * Recreates the line entities for the current text and pushes the result to every viewer.
     */
    void rebuild() {
        int[] oldIds = ids();
        rendered.clear();

        if (visible && npc.getLocation().getWorld() != null) {
            Location base = npc.getLocation();
            for (int index = 0; index < lines.size(); index++) {
                String text = lines.get(index);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                double y = lineY(base, index, lines.size());
                rendered.add(new Line(base.getWorld(), text, base.getX(), y, base.getZ(), index));
            }
        }

        for (UUID viewerId : npc.viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (oldIds.length > 0) {
                NpcPackets.send(player, NpcPackets.destroy(oldIds));
            }
            showTo(player);
        }
    }

    /**
     * Moves the hologram after the NPC was teleported and pushes the new positions.
     */
    void updatePositions() {
        if (rendered.isEmpty()) {
            return;
        }
        Location base = npc.getLocation();
        for (Line line : rendered) {
            line.stand.setLocation(base.getX(), lineY(base, line.index, lines.size()), base.getZ(), 0.0F, 0.0F);
        }
        for (UUID viewerId : npc.viewerIds()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            for (Line line : rendered) {
                NpcPackets.send(player, NpcPackets.teleport(line.stand));
            }
        }
    }

    private double lineY(Location base, int index, int lineCount) {
        return base.getY() + offset - LABEL_OFFSET + (lineCount - 1 - index) * lineSpacing;
    }

    private int[] ids() {
        int[] ids = new int[rendered.size()];
        for (int i = 0; i < rendered.size(); i++) {
            ids[i] = rendered.get(i).stand.getId();
        }
        return ids;
    }

    private static final class Line {

        private final EntityArmorStand stand;
        private final int index;

        private Line(World world, String text, double x, double y, double z, int index) {
            this.index = index;
            this.stand = new EntityArmorStand(((CraftWorld) world).getHandle());
            this.stand.setInvisible(true);
            this.stand.setSmall(true);
            this.stand.setBasePlate(false);
            this.stand.setGravity(false);
            this.stand.setCustomName(text);
            this.stand.setCustomNameVisible(true);
            this.stand.setLocation(x, y, z, 0.0F, 0.0F);
        }
    }
}
