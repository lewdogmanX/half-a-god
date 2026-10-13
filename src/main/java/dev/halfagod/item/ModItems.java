package dev.halfagod.item;

import dev.halfagod.HalfAGod;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's one item: the Eclipse, Iskar's sword (the moon storyline). */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HalfAGod.MODID);
    public static final DeferredItem<EclipseItem> ECLIPSE = ITEMS.register("eclipse", EclipseItem::new);

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.COMBAT) {
                e.accept(ECLIPSE.get());
            }
        });
    }
}
