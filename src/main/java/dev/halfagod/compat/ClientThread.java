package dev.halfagod.compat;

import net.minecraft.client.Minecraft;

/** The game's (render) thread, for work that must not happen on the integrated server's thread. Client only. */
public final class ClientThread {
    private ClientThread() {
    }

    public static boolean offThread() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && !mc.isSameThread();
    }

    public static void later(Runnable r) {
        Minecraft.getInstance().execute(r);
    }
}
