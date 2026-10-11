package dev.halfagod.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.halfagod.HalfAGod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * The story map's art, painted in code when the map first opens: a tileable sheet of old paper and four wax seals
 * (red for done, gold for where you are, a paper token for ready-to-start, grey ash for failed).
 */
final class MapArt {
    static final ResourceLocation PAPER = rl("map_paper");
    static final ResourceLocation WAX = rl("map_seal_wax");
    static final ResourceLocation GOLD = rl("map_seal_gold");
    static final ResourceLocation TOKEN = rl("map_seal_token");
    static final ResourceLocation ASH = rl("map_seal_ash");
    static final int PAPER_SIZE = 256;
    static final int SEAL = 48;

    private static boolean made;

    private MapArt() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalfAGod.MODID, "dynamic/" + path);
    }

    static void ensure() {
        if (made) {
            return;
        }
        made = true;
        try {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            tm.register(PAPER, new DynamicTexture(paper()));
            tm.register(WAX, new DynamicTexture(seal(0x9A1E1E, false, 1)));
            tm.register(GOLD, new DynamicTexture(seal(0xD9A030, false, 2)));
            tm.register(TOKEN, new DynamicTexture(seal(0xF1E4C4, true, 3)));
            tm.register(ASH, new DynamicTexture(seal(0x86807A, false, 4)));
        } catch (Exception e) {
            HalfAGod.LOG.error("Could not paint the story map", e);
        }
    }

    /** NativeImage stores pixels as ABGR. */
    private static int pixel(int a, double r, double g, double b) {
        int ri = clamp(r), gi = clamp(g), bi = clamp(b);
        return (a << 24) | (bi << 16) | (gi << 8) | ri;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    /** Smooth value noise that wraps every px by py cells, so the paper tiles without seams. */
    private static float noise(float x, float y, int px, int py, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(Math.floorMod(x0, px), Math.floorMod(y0, py), seed);
        float b = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0, py), seed);
        float c = hash(Math.floorMod(x0, px), Math.floorMod(y0 + 1, py), seed);
        float d = hash(Math.floorMod(x0 + 1, px), Math.floorMod(y0 + 1, py), seed);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static float fbm(float u, float v, int seed) {
        float sum = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < 5; o++) {
            int p = 4 << o;
            sum += amp * noise(u * p, v * p, p, p, seed + o);
            norm += amp;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static NativeImage paper() {
        int s = PAPER_SIZE;
        NativeImage img = new NativeImage(s, s, false);
        int[][] rings = {{62, 178, 30}, {196, 64, 19}, {150, 214, 9}};
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float u = x / (float) s, v = y / (float) s;
                float n = fbm(u, v, 7);
                float fibre = noise(u * 96, v * 6, 96, 6, 31);
                float blot = fbm(u, v, 91);
                double shade = 1 + (n - 0.5) * 0.17 + (fibre - 0.5) * 0.05 - Math.max(0, blot - 0.62) * 0.35;
                if (hash(x, y, 5) < 0.004f) {
                    shade *= 0.86; // flecks
                }
                for (int[] r : rings) {
                    double d = Math.hypot(x - r[0], y - r[1]);
                    double ring = Math.abs(d - r[2]);
                    if (ring < 1.6) {
                        shade *= 0.93 + ring * 0.03;
                    } else if (d < r[2]) {
                        shade *= 0.985;
                    }
                }
                img.setPixelRGBA(x, y, pixel(255, 233 * shade, 216 * shade, 180 * shade));
            }
        }
        return img;
    }

    private static NativeImage seal(int rgb, boolean token, int seed) {
        int s = SEAL;
        double c = (s - 1) / 2.0;
        int br = (rgb >> 16) & 0xFF, bg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
        NativeImage img = new NativeImage(s, s, false);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double dx = x - c, dy = y - c;
                double r = Math.hypot(dx, dy);
                double a = Math.atan2(dy, dx);
                double edge = token ? 21.5 : 20.6 + 1.4 * Math.sin(5 * a + seed) + 0.8 * Math.sin(11 * a + seed * 2) + 0.5 * Math.sin(17 * a);
                double alpha = Math.max(0, Math.min(1, (edge - r) * 1.3 + 0.5));
                if (alpha <= 0) {
                    img.setPixelRGBA(x, y, 0);
                    continue;
                }
                double light = 1 + 0.26 * (-(dx + dy) / (edge * 1.41));
                double rim = Math.max(0, Math.min(1, (r - (edge - 4)) / 3));
                double cr = br, cg = bg, cb = bb;
                if (token) {
                    light = 1 + 0.08 * (-(dx + dy) / (edge * 1.41));
                    light *= 1 - 0.08 * rim;
                    double ink = 0;
                    if (Math.abs(r - (edge - 2.2)) < 0.9) {
                        ink = 0.85;
                    } else if (Math.abs(r - 15.5) < 0.6 && ((int) Math.floor((a + Math.PI) * 12 / Math.PI)) % 2 == 0) {
                        ink = 0.45;
                    }
                    cr = cr * (1 - ink) + 95 * ink;
                    cg = cg * (1 - ink) + 72 * ink;
                    cb = cb * (1 - ink) + 46 * ink;
                } else {
                    light *= 1 - 0.25 * rim;
                    double groove = Math.abs(r - 15);
                    if (groove < 1.1) {
                        light *= 0.72 + groove * 0.12;
                    } else if (r > 16 && r < 17.2) {
                        light *= 1.07;
                    } else if (r < 14) {
                        light *= 0.94;
                    }
                    double hl = Math.hypot(dx + 8, dy + 8);
                    if (hl < 4.5) {
                        light *= 1 + 0.28 * (1 - hl / 4.5);
                    }
                    light *= 0.96 + hash(x, y, seed) * 0.08;
                }
                img.setPixelRGBA(x, y, pixel((int) Math.round(alpha * 255), cr * light, cg * light, cb * light));
            }
        }
        return img;
    }
}
