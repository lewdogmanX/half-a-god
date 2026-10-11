package dev.halfagod.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
 * /halfagod test story <prologue|act1..act7|end|after|random> [good|evil|waver|random] | story clear | story status   (operators)
 * /halfagod test dream [id] | dreams reset | secret <name> | trial [status|witnesses|day|release|reset] | endcard | codex <page>   (operators)
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
                .then(literal("fates").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!Fates.storyOver(Meb.traits(p))) {
                        c.getSource().sendFailure(Component.literal("Your story isn't over yet."));
                        return 0;
                    }
                    Net.send(p, new dev.halfagod.net.Payloads.OpenMap("fates"));
                    return 1;
                }))
                .then(literal("map").executes(c -> {
                    Net.send(c.getSource().getPlayerOrException(),
                            new dev.halfagod.net.Payloads.OpenMap(""));
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
                .then(test())
                .then(literal("setrep").requires(s -> s.hasPermission(2))
                        .then(argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(c -> setRep(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "value")))
                                .then(argument("player", EntityArgument.player())
                                        .executes(c -> setRep(EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "value")))))));
    }

    // ------------------------------------------------------------------ /halfagod test ... (operators): try the new things quickly
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> test() {
        return literal("test").requires(s -> s.hasPermission(2))
                .then(literal("dream")
                        .executes(c -> dream(c.getSource(), ""))
                        .then(argument("id", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (com.google.gson.JsonObject d : StoryData.INSTANCE.dreams) {
                                        b.suggest(d.get("id").getAsString());
                                    }
                                    StoryData.INSTANCE.specialDreams.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(c -> dream(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(literal("dreams").then(literal("reset").executes(c -> {
                    RepData d = Reputation.data(c.getSource().getPlayerOrException());
                    d.dreams.clear();
                    d.lastDreamNight = -10;
                    return ok(c.getSource(), "Dreams forgotten. The next full night's sleep can bring one (1 in 4).");
                })))
                .then(literal("secret")
                        .then(argument("name", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (com.google.gson.JsonObject l : StoryData.INSTANCE.secrets) {
                                        b.suggest(l.get("key").getAsString());
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    String key = StringArgumentType.getString(c, "name");
                                    boolean found = Secrets.find(c.getSource().getPlayerOrException(), key, 20);
                                    return ok(c.getSource(), found ? "Found the hidden questline \"" + key + "\"." : "Unknown, or already found: " + key);
                                })))
                .then(literal("trial")
                        .executes(c -> ok(c.getSource(), Trial.testStart(c.getSource().getPlayerOrException())))
                        .then(literal("status").executes(c -> ok(c.getSource(), Trial.status(c.getSource().getPlayerOrException()))))
                        .then(literal("witnesses").executes(c -> ok(c.getSource(), Trial.testWitnesses(c.getSource().getPlayerOrException()))))
                        .then(literal("day").executes(c -> ok(c.getSource(), Trial.testDay(c.getSource().getPlayerOrException()))))
                        .then(literal("release").executes(c -> ok(c.getSource(), Trial.testRelease(c.getSource().getPlayerOrException()))))
                        .then(literal("reset").executes(c -> {
                            RepData d = Reputation.data(c.getSource().getPlayerOrException());
                            d.trialActs.clear();
                            d.convictions = 0;
                            return ok(c.getSource(), "The Watch has forgotten you (this act's trial can happen again).");
                        })))
                .then(literal("story")
                        .then(literal("clear").executes(c -> ok(c.getSource(), Simulate.clear(c.getSource().getPlayerOrException()))))
                        .then(literal("status").executes(c -> ok(c.getSource(), Simulate.status(c.getSource().getPlayerOrException()))))
                        .then(argument("point", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    Simulate.acts().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(c -> ok(c.getSource(), Simulate.run(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "point"), "random")))
                                .then(argument("as", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (String m : new String[]{"good", "evil", "waver", "random"}) {
                                                b.suggest(m);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(c -> ok(c.getSource(), Simulate.run(c.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(c, "point"), StringArgumentType.getString(c, "as")))))))
                .then(literal("endcard").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Net.send(p, new Payloads.OpenMap("story"));
                    return ok(c.getSource(), "Opened \"Your Story\" (a preview until the story is really over).");
                }))
                .then(literal("codex")
                        .then(argument("page", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String t : new String[]{"lore", "letters", "path", "dreams", "secrets", "trials", "story", "fates"}) {
                                        b.suggest(t);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    Net.send(c.getSource().getPlayerOrException(), new Payloads.OpenMap(StringArgumentType.getString(c, "page")));
                                    return 1;
                                })));
    }

    private static int dream(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        com.google.gson.JsonObject d = id.isEmpty() ? Dreams.pick(p, Reputation.data(p)) : Dreams.byId(id);
        if (d == null) {
            source.sendFailure(Component.literal(id.isEmpty() ? "No dream left that fits where you are in the story." : "No dream called " + id));
            return 0;
        }
        if (StoryData.INSTANCE.specialDreams.containsValue(d)) {
            Dreams.send(p, d);
        } else {
            Dreams.dream(p, d);
        }
        return 1;
    }

    private static int ok(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int openStory(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Net.send(source.getPlayerOrException(), new Payloads.OpenStory(true));
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
