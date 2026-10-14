package dev.halfagod.item;

import dev.halfagod.HalfAGod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's items, and its own creative tab ("Half a God", with the modpack's seal for an icon), which holds every item
 * the mod adds (and every block, when there are blocks: they register their items here too, so they're in it).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HalfAGod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HalfAGod.MODID);

    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HalfAGod.MODID);
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> MARACA_SHAKE = SOUNDS.register("item.golden_maraca.shake",
            () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "item.golden_maraca.shake")));

    public static final DeferredItem<EclipseItem> ECLIPSE = ITEMS.register("eclipse", EclipseItem::new);
    public static final DeferredItem<GoldenMaracaItem> GOLDEN_MARACA = ITEMS.register("golden_maraca", GoldenMaracaItem::new);
    /** only the creative tab's icon (the modpack's seal); never in the tab itself, never in survival */
    public static final DeferredItem<Item> TAB_ICON = ITEMS.registerSimpleItem("tab_icon", new Item.Properties());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("half_a_god", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.half_a_god"))
            .icon(() -> new ItemStack(TAB_ICON.get()))
            .displayItems((params, out) -> {
                for (var item : ITEMS.getEntries()) {
                    if (item != TAB_ICON) {
                        out.accept(item.get());
                    }
                }
            })
            .build());

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            // Cult of Azazel's guidebook (its tutorial) is gone from the game, the creative menu too
            var guide = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("netherman", "azazel_guide_book"));
            if (guide != net.minecraft.world.item.Items.AIR) {
                // (the event's remove(stack, visibility); looked up by name so a change in NeoForge can't break the build)
                try {
                    e.getClass().getMethod("remove", ItemStack.class, CreativeModeTab.TabVisibility.class)
                            .invoke(e, new ItemStack(guide), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // not in this tab, or no way to take it out: it still vanishes the moment anyone holds it
                }
            }
        });
    }
}
