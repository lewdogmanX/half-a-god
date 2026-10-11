package dev.halfagod.net;

import dev.halfagod.HalfAGod;
import dev.halfagod.core.Choices;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All packets. Client handlers live in dev.halfagod.client.ClientHooks and only load on the client. */
public final class Payloads {
    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, path));
    }

    /** Server -> client: current reputation (x10) and the last change (x10). */
    public record RepSync(int value10, int delta10) implements CustomPacketPayload {
        public static final Type<RepSync> TYPE = typeOf("rep");
        public static final StreamCodec<ByteBuf, RepSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RepSync::value10, ByteBufCodecs.VAR_INT, RepSync::delta10, RepSync::new);

        @Override
        public Type<RepSync> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the story viewer (operators only). */
    public record OpenStory(boolean op) implements CustomPacketPayload {
        public static final Type<OpenStory> TYPE = typeOf("open_story");
        public static final StreamCodec<ByteBuf, OpenStory> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, OpenStory::op, OpenStory::new);

        @Override
        public Type<OpenStory> type() {
            return TYPE;
        }
    }

    /** Server -> client: show a choice screen (the choice as JSON). */
    public record OpenChoice(String json) implements CustomPacketPayload {
        public static final Type<OpenChoice> TYPE = typeOf("open_choice");
        public static final StreamCodec<ByteBuf, OpenChoice> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenChoice::json, OpenChoice::new);

        @Override
        public Type<OpenChoice> type() {
            return TYPE;
        }
    }

    /** Server -> client: a cinematic moment. */
    public record Cinematic(String title, String subtitle, int color, String sound, int shakeTicks, float shake)
            implements CustomPacketPayload {
        public static final Type<Cinematic> TYPE = typeOf("cinematic");
        public static final StreamCodec<ByteBuf, Cinematic> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Cinematic::title, ByteBufCodecs.STRING_UTF8, Cinematic::subtitle,
                ByteBufCodecs.INT, Cinematic::color, ByteBufCodecs.STRING_UTF8, Cinematic::sound,
                ByteBufCodecs.VAR_INT, Cinematic::shakeTicks, ByteBufCodecs.FLOAT, Cinematic::shake, Cinematic::new);

        @Override
        public Type<Cinematic> type() {
            return TYPE;
        }
    }

    /** Client -> server: the player picked an answer. */
    public record PickChoice(String id, int option) implements CustomPacketPayload {
        public static final Type<PickChoice> TYPE = typeOf("pick_choice");
        public static final StreamCodec<ByteBuf, PickChoice> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PickChoice::id, ByteBufCodecs.VAR_INT, PickChoice::option, PickChoice::new);

        @Override
        public Type<PickChoice> type() {
            return TYPE;
        }
    }

    /** Client -> server: the story map wants to know how far along you are. */
    public record RequestMap(boolean all) implements CustomPacketPayload {
        public static final Type<RequestMap> TYPE = typeOf("request_map");
        public static final StreamCodec<ByteBuf, RequestMap> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, RequestMap::all, RequestMap::new);

        @Override
        public Type<RequestMap> type() {
            return TYPE;
        }
    }

    /** Server -> client: quest states for the story map (JSON: id -> done/active/failed/open, plus outcome). */
    public record MapState(String json) implements CustomPacketPayload {
        public static final Type<MapState> TYPE = typeOf("map_state");
        public static final StreamCodec<ByteBuf, MapState> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1 << 20), MapState::json, MapState::new);

        @Override
        public Type<MapState> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the story map. */
    public record OpenMap(boolean fates) implements CustomPacketPayload {
        public static final Type<OpenMap> TYPE = typeOf("open_map");
        public static final StreamCodec<ByteBuf, OpenMap> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, OpenMap::fates, OpenMap::new);

        @Override
        public Type<OpenMap> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RepSync.TYPE, RepSync.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onRep(p)));
        r.playToClient(OpenStory.TYPE, OpenStory.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onOpenStory(p)));
        r.playToClient(OpenChoice.TYPE, OpenChoice.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onOpenChoice(p)));
        r.playToClient(Cinematic.TYPE, Cinematic.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onCinematic(p)));
        r.playToClient(MapState.TYPE, MapState.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onMapState(p)));
        r.playToClient(OpenMap.TYPE, OpenMap.CODEC, (p, ctx) -> ctx.enqueueWork(() -> dev.halfagod.client.ClientHooks.onOpenMap(p)));
        r.playToServer(RequestMap.TYPE, RequestMap.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                dev.halfagod.core.MapStates.send(sp);
            }
        }));
        r.playToServer(PickChoice.TYPE, PickChoice.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Choices.pick(sp, p.id(), p.option());
            }
        }));
    }
}
