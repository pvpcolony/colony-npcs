package me.npclib;

/**
 * Equipment slots understood by the 1.8 {@code PacketPlayOutEntityEquipment} packet.
 */
public enum NpcSlot {

    /** Item held in the main hand. */
    HAND(0),
    BOOTS(1),
    LEGGINGS(2),
    CHESTPLATE(3),
    HELMET(4);

    private final int protocolId;

    NpcSlot(int protocolId) {
        this.protocolId = protocolId;
    }

    /**
     * @return the slot id used by the 1.8 protocol
     */
    public int getProtocolId() {
        return protocolId;
    }
}
