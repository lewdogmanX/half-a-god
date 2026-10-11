package dev.halfagod.mixin;

import com.google.common.collect.HashMultimap;
import dev.halfagod.compat.ClientThread;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClientEvents;
import folk.sisby.surveyor.util.RegionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Antique Atlas + Surveyor crash fix (singleplayer). For the world's host, Surveyor fires its *client* map events
 * straight from the server thread, and Antique Atlas stores what they bring in plain maps that the game's own thread
 * reads and writes at the same time: sooner or later that's a ConcurrentModificationException and a crash.
 * Here those events are handed to the game's thread instead, so only one thread ever touches Antique Atlas's maps.
 */
@Pseudo
@Mixin(targets = "folk.sisby.surveyor.client.SurveyorClientEvents$Invoke", remap = false)
public abstract class SurveyorClientEventsMixin {
    @Inject(method = "terrainUpdated(Lfolk/sisby/surveyor/WorldSummary;Ljava/util/Map;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void halfagod$terrainOnGameThread(WorldSummary summary, Map<RegionPos, BitSet> chunks, CallbackInfo ci) {
        if (ClientThread.offThread()) {
            Map<RegionPos, BitSet> copy = new HashMap<>();
            chunks.forEach((k, v) -> copy.put(k, (BitSet) v.clone()));
            ClientThread.later(() -> SurveyorClientEvents.Invoke.terrainUpdated(summary, copy));
            ci.cancel();
        }
    }

    @Inject(method = "structuresAdded(Lfolk/sisby/surveyor/WorldSummary;Lcom/google/common/collect/Multimap;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void halfagod$structuresOnGameThread(WorldSummary summary, Multimap<?, ?> starts, CallbackInfo ci) {
        if (ClientThread.offThread()) {
            Multimap<?, ?> copy = HashMultimap.create(starts);
            ClientThread.later(() -> SurveyorClientEvents.Invoke.structuresAdded(summary, copy));
            ci.cancel();
        }
    }

    @Inject(method = "landmarksAdded(Lfolk/sisby/surveyor/WorldSummary;Lcom/google/common/collect/Multimap;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void halfagod$landmarksAddedOnGameThread(WorldSummary summary, Multimap<?, ?> landmarks, CallbackInfo ci) {
        if (ClientThread.offThread()) {
            Multimap<?, ?> copy = HashMultimap.create(landmarks);
            ClientThread.later(() -> SurveyorClientEvents.Invoke.landmarksAdded(summary, copy));
            ci.cancel();
        }
    }

    @Inject(method = "landmarksRemoved(Lfolk/sisby/surveyor/WorldSummary;Lcom/google/common/collect/Multimap;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void halfagod$landmarksRemovedOnGameThread(WorldSummary summary, Multimap<?, ?> landmarks, CallbackInfo ci) {
        if (ClientThread.offThread()) {
            Multimap<?, ?> copy = HashMultimap.create(landmarks);
            ClientThread.later(() -> SurveyorClientEvents.Invoke.landmarksRemoved(summary, copy));
            ci.cancel();
        }
    }
}
