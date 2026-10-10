package dev.halfagod.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.halfagod.net.Payloads;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /halfagod rep | lostmyway | choose | chronicle            (everyone)
 * /halfagod story | unlockall | jump <quest> [player] | setrep <value> [player]   (operators)
 */
public final class Commands {
    private Commands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(literal("halfagod")
                .then(literal("rep").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    double v = Reputation.data(p).value;
                    int t = Reputation.tierIndex(v);
                    c.getSource().sendSuccess(() -> Component.literal("Reputation: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(Reputation.TIERS[t] + " (" + Math.round(v) + ")").withStyle(Reputation.color(t))), false);
                    return 1;
                }))
                .then(literal("lostmyway").executes(c -> {
                    StoryGuard.lostMyWay(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(literal("choose").executes(c -> {
                    int n = Choices.offerPending(c.getSource().getPlayerOrException(), true);
                    if (n == 0) {
                        c.getSource().sendSuccess(() -> Component.literal("You have no decisions waiting."), false);
                    }
                    return 1;
                }))
                .then(literal("travel").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!Morality.inVillage(p.serverLevel(), p.blockPosition())) {
                        c.getSource().sendFailure(Component.literal("You can only set off from a village."));
                        return 0;
                    }
                    Travel.open(p);
                    return 1;
                }))
                .then(literal("map").executes(c -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(c.getSource().getPlayerOrException(),
                            new dev.halfagod.net.Payloads.OpenMap(false));
                    return 1;
                }))
                .then(literal("chronicle").executes(c -> {
                    Chronicle.give(c.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(literal("story").requires(s -> s.hasPermission(2)).executes(c -> openStory(c.getSource())))
                .then(literal("unlockall").requires(s -> s.hasPermission(2)).executes(c -> openStory(c.getSource())))
                .then(literal("jump").requires(s -> s.hasPermission(2))
                        .then(argument("quest", ResourceLocationArgument.id())
                                .executes(c -> jump(c.getSource(), ResourceLocationArgument.getId(c, "quest"), c.getSource().getPlayerOrException()))
                                .then(argument("player", EntityArgument.player())
                                        .executes(c -> jump(c.getSource(), ResourceLocationArgument.getId(c, "quest"), EntityArgument.getPlayer(c, "player"))))))
                .then(literal("setrep").requires(s -> s.hasPermission(2))
                        .then(argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(c -> setRep(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "value")))
                                .then(argument("player", EntityArgument.player())
                                        .executes(c -> setRep(EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "value")))))));
    }

    private static int openStory(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PacketDistributor.sendToPlayer(source.getPlayerOrException(), new Payloads.OpenStory(true));
        return 1;
    }

    private static int jump(CommandSourceStack source, ResourceLocation quest, ServerPlayer player) {
        ResourceLocation id = quest.getNamespace().equals("minecraft") && !quest.getPath().contains(":")
                ? ResourceLocation.fromNamespaceAndPath("mebahelrpgrquest", quest.getPath().startsWith("hag_") ? quest.getPath() : "hag_" + quest.getPath())
                : quest;
        StoryData.Graph g = StoryData.INSTANCE.graph.get(id.toString());
        if (g != null && g.book()) {
            Meb.giveBook(player, id.toString());
            source.sendSuccess(() -> Component.literal("Gave the letter " + id), true);
            return 1;
        }
        boolean ok = Meb.startQuest(player, id, false);
        source.sendSuccess(() -> Component.literal(ok ? "Started " + id : "Couldn't start " + id + " (unknown, already active, or its requirements aren't met)"), true);
        return ok ? 1 : 0;
    }

    private static int setRep(ServerPlayer player, int value) {
        RepData d = Reputation.data(player);
        Reputation.change(player, value - d.value, "set by an operator");
        return 1;
    }
}
