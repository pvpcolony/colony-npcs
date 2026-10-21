package me.npclib;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.server.v1_8_R3.PacketPlayInUseEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Listens for the client's use-entity packet, which is the only way an interaction with a
 * packet-only NPC reaches the server. One handler is added to the netty pipeline of every
 * player and removed again on quit and shutdown.
 * <p>
 * Internal: use {@link Npc#action(java.util.function.Consumer)} instead.
 */
final class InteractionInjector {

    private static final String HANDLER_NAME = "npclib-interact";
    private static final String PACKET_HANDLER = "packet_handler";
    private static final Field ENTITY_ID = entityIdField();

    private InteractionInjector() {
    }

    /**
     * Adds the interaction handler to the player's pipeline. Safe to call repeatedly.
     */
    static void inject(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Channel channel = channel(player);
        if (channel == null) {
            return;
        }

        final ChannelPipeline pipeline = channel.pipeline();
        try {
            ChannelHandler existing = pipeline.get(HANDLER_NAME);
            if (existing != null) {
                if (existing.getClass() == Handler.class) {
                    // Already hooked by this library instance.
                    return;
                }
                // Left over from a plugin reloaded in a different class loader; replace it
                // so clicks are dispatched to the current library instance.
                pipeline.remove(existing);
            }

            Handler handler = new Handler(player.getUniqueId());
            if (pipeline.get(PACKET_HANDLER) != null) {
                pipeline.addBefore(PACKET_HANDLER, HANDLER_NAME, handler);
            } else {
                pipeline.addLast(HANDLER_NAME, handler);
            }
        } catch (RuntimeException exception) {
            // The channel was closed or the pipeline changed between the checks above.
            // Without a hook the NPCs simply do not react to clicks for this session.
            NpcLib.reportError("Could not hook interactions for " + player.getName(), exception);
        }
    }

    /**
     * Removes the interaction handler from the player's pipeline, if present.
     */
    static void uninject(Player player) {
        Channel channel = player == null ? null : channel(player);
        if (channel == null) {
            return;
        }

        ChannelPipeline pipeline = channel.pipeline();
        try {
            if (pipeline.get(HANDLER_NAME) != null) {
                pipeline.remove(HANDLER_NAME);
            }
        } catch (RuntimeException ignored) {
            // Channel already closed, nothing left to clean up.
        }
    }

    /**
     * @return whether the player's pipeline currently has the interaction handler
     */
    static boolean isInjected(Player player) {
        Channel channel = player == null ? null : channel(player);
        return channel != null && channel.pipeline().get(HANDLER_NAME) != null;
    }

    private static Channel channel(Player player) {
        if (!(player instanceof CraftPlayer)) {
            return null;
        }
        return ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
    }

    /**
     * @return the entity id the client interacted with
     */
    static int entityId(PacketPlayInUseEntity packet) {
        try {
            return ENTITY_ID.getInt(packet);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read the entity id of a use-entity packet", exception);
        }
    }

    /**
     * @return {@code false} for attacks (left clicks), which are not interactions
     */
    static boolean isInteraction(PacketPlayInUseEntity packet) {
        return packet.a() != PacketPlayInUseEntity.EnumEntityUseAction.ATTACK;
    }

    private static Field entityIdField() {
        try {
            Field field = PacketPlayInUseEntity.class.getDeclaredField("a");
            field.setAccessible(true);
            return field;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /**
     * Runs on the netty thread: only reads the entity id and schedules the callback.
     */
    private static final class Handler extends ChannelInboundHandlerAdapter {

        private final UUID playerId;

        private Handler(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (message instanceof PacketPlayInUseEntity) {
                handleUseEntity((PacketPlayInUseEntity) message);
            }
            super.channelRead(context, message);
        }

        private void handleUseEntity(PacketPlayInUseEntity packet) {
            try {
                // Attacks (left clicks) are not interactions, leave them to the client.
                if (!isInteraction(packet)) {
                    return;
                }

                int entityId = entityId(packet);
                if (NpcLib.isInteractiveEntity(entityId)) {
                    NpcLib.handleInteraction(playerId, entityId);
                }
            } catch (Exception exception) {
                // A broken packet must never disconnect the player, but it also must not be
                // hidden completely.
                NpcLib.reportError("Could not read an interaction packet", exception);
            }
        }
    }
}
