"""The moon storyline's three places, drawn block by block (written as structure templates by the build):

- the Observatory: a round stone tower on a mountain with a green copper dome, a slit open to the sky and a great
  brass telescope pointed through it. Inside, star charts, books, a cartography table, a bed, and the astronomer (a
  cartographer the story's Hester Quill takes the place of).
- the Faithful's wreck: their crude gold rocket, broken in a crater on the moon, with its pilgrims still kneeling in
  their gold, and a barrel for their logbook.
- Iskar's rest: the Silver King's corpse lying on his back across the great crater, pale stone burned black and gold
  where the sun struck him, a silver crown, his mouth open, his sword the Eclipse across his body."""
import math
import random

from nbt import Canvas, Byte, Float


def _r(seed):
    return random.Random(seed)


# ------------------------------------------------------------------ helpers
CARD = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}


def toward(dx, dz):
    """the cardinal direction closest to (dx, dz)"""
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def ring_path(cells, cx, cz, a0, a1):
    """cells of a ring between two angles (degrees, 0 = south, 90 = east), in order, 4-connected"""
    def ang(p):
        return math.degrees(math.atan2(p[0] - cx, p[1] - cz)) % 360
    inside = (lambda a: a0 <= a <= a1) if a0 < a1 else (lambda a: a >= a0 or a <= a1)
    pick = sorted((p for p in cells if inside(ang(p))), key=lambda p: (ang(p) - a0) % 360)
    out = [pick[0]]
    for p in pick[1:]:
        q = out[-1]
        if abs(p[0] - q[0]) + abs(p[1] - q[1]) == 2:      # a diagonal step: go round the corner
            out.append((p[0], q[1]) if (p[0], q[1]) in cells else (q[0], p[1]))
        out.append(p)
    return out


# ------------------------------------------------------------------ the Observatory
def observatory():
    """A round stone tower on a raised terrace, with dark buttresses, a cornice and a walk-round balcony; a ribbed copper
    dome with a slit, and the brass telescope pointing out of it at the moon. Inside: a study under a gallery, a spiral
    stair, star charts. To the east, Hester's cottage wing (bed, desk, stove), with a chimney."""
    c = Canvas()
    rng = _r(11)
    cx, cz = 14, 14
    R = 6          # tower wall radius
    TOP = 10       # the top of the wall
    YD = TOP + 1   # the dome's centre
    RD = 6         # dome radius

    def d(x, z):
        return math.hypot(x - cx, z - cz)

    def cells(r0, r1, pad=4):
        return [(x, z) for x in range(cx - r1 - pad, cx + r1 + pad + 1) for z in range(cz - r1 - pad, cz + r1 + pad + 1)
                if r0 - 0.5 <= d(x, z) < r1 + 0.5]

    def stone(y):
        r = rng.random()
        moss = 0.16 if y <= 2 else 0.07 if y <= 5 else 0.03
        if r < moss:
            return "minecraft:mossy_stone_bricks"
        if r < moss + 0.1:
            return "minecraft:cracked_stone_bricks"
        return "minecraft:stone_bricks"

    annex = lambda x, z: cx + 5 <= x <= cx + 13 and cz - 4 <= z <= cz + 4 and d(x, z) >= R - 0.5

    # ---- foundation and terrace (the terrace's top is level with the floor; its edge is a step down)
    for (x, z) in cells(0, R + 3):
        c.set(x, -1, z, "minecraft:stone_bricks")
    for (x, z) in cells(0, R + 2):
        dd = d(x, z)
        if dd >= R + 1.5:
            c.set(x, 0, z, "minecraft:stone_bricks")
        elif dd >= R - 0.5:
            c.set(x, 0, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:andesite")
    for (x, z) in cells(R + 3, R + 3):
        c.set(x, 0, z, f"minecraft:stone_brick_stairs[facing={toward(cx - x, cz - z)},half=bottom]")
    # keep the terrace and the rooms clear of hillside
    for y in range(1, 5):
        for (x, z) in cells(0, R + 3):
            c.set(x, y, z, "minecraft:air")
    # ---- the floor inside: planks with a compass rose
    for (x, z) in cells(0, R - 1):
        dd = d(x, z)
        b = "minecraft:spruce_planks"
        if x == cx or z == cz:
            b = "minecraft:dark_oak_planks"
        if 2.5 <= dd < 3.5:
            b = "minecraft:stripped_spruce_log[axis=y]"
        c.set(x, 0, z, b)
    c.set(cx, 0, cz - R + 1, "minecraft:chiseled_stone_bricks")   # north
    # ---- the walls, the string course and the top course
    wall = cells(R, R)
    for y in range(1, TOP + 1):
        for (x, z) in wall:
            c.set(x, y, z, "minecraft:polished_deepslate" if y in (5, TOP) else stone(y))
    for y in range(1, YD + RD + 1):
        for (x, z) in cells(0, R - 1):
            if y > 0:
                c.set(x, y, z, "minecraft:air")
    # ---- buttresses between the windows
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        x, z = cx + round((R + 1) * math.sin(a)), cz + round((R + 1) * math.cos(a))
        if annex(x, z):
            continue
        c.set(x, 1, z, "minecraft:polished_deepslate")
        for y in range(2, TOP):
            c.set(x, y, z, "minecraft:deepslate_bricks" if y != 5 else "minecraft:polished_deepslate")
    # ---- windows (two rows; the south one is the door, the east one opens into the cottage)
    for k in range(8):
        a = math.radians(45 * k)
        x, z = cx + round(R * math.sin(a)), cz + round(R * math.cos(a))
        if k == 0:
            continue
        if k == 2:
            for y in (1, 2):
                c.set(x, y, z, "minecraft:air")
            c.set(x, 3, z, "minecraft:chiseled_stone_bricks")
            continue
        for y in (2, 3):
            c.set(x, y, z, "minecraft:glass_pane")
        for y in (7, 8, 9):
            c.set(x, y, z, "minecraft:glass_pane")
        c.set(x, 4, z, "minecraft:chiseled_stone_bricks")
    # the door, its frame, and a little porch roof with lanterns
    dz = cz + R
    c.set(cx, 1, dz, "minecraft:spruce_door[facing=north,half=lower,hinge=left]")
    c.set(cx, 2, dz, "minecraft:spruce_door[facing=north,half=upper,hinge=left]")
    for x in (cx - 1, cx + 1):
        for y in (1, 2, 3):
            c.set(x, y, dz, "minecraft:polished_deepslate")
    c.set(cx, 3, dz, "minecraft:chiseled_stone_bricks")
    for x in (cx - 1, cx, cx + 1):
        c.set(x, 4, dz + 1, "minecraft:deepslate_tile_slab[type=bottom]")
    c.set(cx - 1, 4, dz + 1, "minecraft:deepslate_tile_stairs[facing=east,half=bottom]")
    c.set(cx + 1, 4, dz + 1, "minecraft:deepslate_tile_stairs[facing=west,half=bottom]")
    for x in (cx - 1, cx + 1):
        c.set(x, 3, dz + 1, "minecraft:lantern[hanging=true]")
    # ---- cornice, balcony and railing at the top of the wall
    for (x, z) in cells(R + 1, R + 1):
        if True:
            c.set(x, TOP, z, f"minecraft:deepslate_tile_stairs[facing={toward(cx - x, cz - z)},half=top]")
    for (x, z) in cells(R + 2, R + 2):
        if True:
            c.set(x, TOP, z, "minecraft:deepslate_tile_slab[type=top]")
            c.set(x, TOP + 1, z, "minecraft:deepslate_brick_wall")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        x, z = cx + round((R + 2) * math.sin(a)), cz + round((R + 2) * math.cos(a))
        c.set(x, TOP + 2, z, "minecraft:lantern[hanging=false]")

    # ---- the dome: a smooth copper shell with ribs, and the slit
    shell = {}
    for y in range(YD, YD + RD + 1):
        for x in range(cx - RD - 1, cx + RD + 2):
            for z in range(cz - RD - 1, cz + RD + 2):
                r3 = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - YD) * 1.08) ** 2)
                if RD - 0.55 <= r3 < RD + 0.5 or (y == YD + RD and r3 < RD + 0.5):
                    shell[(x, y, z)] = True
    slit = lambda x, y, z: abs(x - cx) <= 1 and z <= cz + 1 and y > YD
    rail = lambda x, y, z: abs(x - cx) == 2 and z <= cz + 2 and y > YD
    ribs = set()
    for k in range(8):
        a = math.radians(45 * k)
        if k == 4:
            continue
        for y in range(YD, YD + RD + 1):
            rr = math.sqrt(max(0.0, (RD + 0.1) ** 2 - ((y - YD) * 1.08) ** 2))
            ribs.add((cx + round(rr * math.sin(a)), y, cz + round(rr * math.cos(a))))
    for (x, y, z) in shell:
        if slit(x, y, z):
            continue
        out_dx, out_dz = x - cx, z - cz
        above = (x, y + 1, z) in shell and not slit(x, y + 1, z)
        step = toward(out_dx, out_dz)
        ox, oz = CARD[step]
        outside_free = (x + ox, y, z + oz) not in shell
        if rail(x, y, z):
            mat = "waxed_cut_copper"
        elif (x, y, z) in ribs:
            mat = "waxed_weathered_cut_copper"
        else:
            mat = "waxed_oxidized_cut_copper"
        if not above and outside_free and y > YD and (out_dx or out_dz) and math.hypot(out_dx, out_dz) > 1.6:
            inward = toward(cx - x, cz - z)
            c.set(x, y, z, f"minecraft:{mat}_stairs[facing={inward},half=bottom]")
        else:
            c.set(x, y, z, f"minecraft:{mat}")
    for y in range(YD, YD + RD + 1):     # the inside of the dome is clear
        for x in range(cx - RD, cx + RD + 1):
            for z in range(cz - RD, cz + RD + 1):
                if (x, y, z) not in shell and math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - YD) * 1.08) ** 2) < RD - 0.5:
                    c.set(x, y, z, "minecraft:air")
    for (x, y, z) in shell:
        if slit(x, y, z):
            c.set(x, y, z, "minecraft:air")
    # the finial
    c.set(cx, YD + RD + 1, cz + 2, "minecraft:lightning_rod[facing=up]")

    # ---- the telescope: a pier, a mount, and a brass tube out through the slit
    for y in range(1, 9):
        c.set(cx, y, cz, "minecraft:polished_deepslate" if y > 1 else "minecraft:chiseled_polished_blackstone")
    for (x, z) in ((cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)):
        c.set(x, 1, z, f"minecraft:polished_deepslate_stairs[facing={toward(cx - x, cz - z)},half=bottom]")
    c.set(cx, 9, cz, "minecraft:waxed_chiseled_copper")
    px, py, pz = cx + 0.5, 10.5, cz + 0.5
    ang = math.radians(38)
    ay, az = math.sin(ang), -math.cos(ang)
    T0, T1 = -4.2, 13.4          # from the eyepiece end to the lens
    tube = {}
    for x in range(cx - 3, cx + 4):
        for y in range(4, YD + RD + 8):
            for z in range(cz - 14, cz + 7):
                vx, vy, vz = x + 0.5 - px, y + 0.5 - py, z + 0.5 - pz
                t = vy * ay + vz * az
                if T0 <= t <= T1:
                    perp = math.sqrt(max(0.0, vx * vx + vy * vy + vz * vz - t * t))
                    rad = 1.5 if t > T1 - 2.6 else 1.08
                    if perp <= rad:
                        tube[(x, y, z)] = (t, perp)
    for (x, y, z), (t, perp) in tube.items():
        if t > T1 - 0.9 and perp < 0.9:
            b = "minecraft:tinted_glass"                        # the lens, looking at the moon
        elif t > T1 - 2.6:
            b = "minecraft:waxed_cut_copper" if t > T1 - 0.9 or t < T1 - 2.1 else "minecraft:waxed_copper_block"
        elif min(abs(t - k) for k in (-2.5, 1.5, 5.5, 9.0)) < 0.5:
            b = "minecraft:polished_blackstone"                 # the bands
        else:
            b = "minecraft:waxed_copper_block"
        c.set(x, y, z, b)
    ex, ey, ez = px, py + (T0 - 0.6) * ay, pz + (T0 - 0.6) * az
    c.set(int(math.floor(ex)), int(math.floor(ey)), int(math.floor(ez)), "minecraft:lightning_rod[facing=south]")   # the eyepiece

    # ---- the gallery, its railing, and the spiral stair up to it
    GY = 5
    for (x, z) in cells(R - 2, R - 1, pad=0):
        if not (abs(x - cx) <= 1 and z < cz):        # (leave the north open, under the slit)
            c.set(x, GY, z, "minecraft:spruce_planks")
    inner = [(x, z) for (x, z) in cells(R - 2, R - 2, pad=0)]
    for (x, z) in inner:
        if not (abs(x - cx) <= 1 and z < cz):
            c.set(x, GY + 1, z, "minecraft:spruce_fence")
    ring = set(cells(R - 1, R - 1, pad=0))
    path = ring_path(ring, cx, cz, 190, 330)[: GY]
    for k, (x, z) in enumerate(path):
        y = k + 1
        nx, nz = path[k + 1] if k + 1 < len(path) else (x + (x - path[k - 1][0]), z + (z - path[k - 1][1]))
        c.set(x, y, z, f"minecraft:spruce_stairs[facing={toward(nx - x, nz - z)},half=bottom]")
        for yy in range(1, y):
            c.set(x, yy, z, "minecraft:stripped_spruce_log[axis=y]")
        for yy in range(y + 1, y + 4):
            if yy == GY or c.get(x, yy, z) in (None, "minecraft:air", "minecraft:spruce_fence"):
                c.set(x, yy, z, "minecraft:air")
        # (and clear the gallery floor over the steps you climb through)
        for (gx, gz) in ((x, z),) + tuple((x + a, z + b) for a, b in CARD.values()):
            if c.get(gx, GY, gz) == "minecraft:spruce_planks" and y >= GY - 3 and y < GY:
                c.set(gx, GY, gz, "minecraft:air")
                c.set(gx, GY + 1, gz, "minecraft:air")

    # ---- the study (ground floor)
    shelves = [p for p in ring if c.get(p[0], 1, p[1]) in (None, "minecraft:air")]
    for (x, z) in shelves:
        a = math.degrees(math.atan2(x - cx, z - cz)) % 360
        if 125 <= a <= 165 or 195 <= a <= 235:            # either side of the north window
            for y in (1, 2, 3):
                c.set(x, y, z, "minecraft:bookshelf" if (x + z + y) % 4 else "minecraft:chiseled_bookshelf[facing=" + toward(cx - x, cz - z) + "]")
            c.set(x, 4, z, "minecraft:spruce_slab[type=bottom]")
    c.set(cx - 3, 1, cz - 3, "minecraft:cartography_table")
    c.set(cx - 4, 1, cz - 2, "minecraft:lectern[facing=east]")
    c.set(cx + 3, 1, cz - 3, "minecraft:spruce_stairs[facing=south,half=bottom]")
    c.set(cx + 3, 1, cz - 4, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]")
    c.set(cx + 4, 1, cz - 3, "minecraft:spruce_trapdoor[facing=west,half=top,open=false]")
    c.set(cx + 3, 2, cz - 4, "minecraft:candle[candles=3,lit=false]")
    c.set(cx + 4, 1, cz + 3, "minecraft:barrel[facing=up]", {"Items": []})
    c.set(cx + 4, 2, cz + 3, "minecraft:potted_fern")
    c.set(cx - 4, 1, cz + 3, "minecraft:chest[facing=east]", {"Items": []})
    for (x, z) in ((cx - 1, cz + 2), (cx, cz + 2), (cx + 1, cz + 2), (cx - 1, cz + 3), (cx, cz + 3), (cx + 1, cz + 3)):
        c.set(x, 1, z, "minecraft:blue_carpet")
    c.set(cx, 1, cz + 3, "minecraft:yellow_carpet")
    # lanterns hung under the gallery, and star charts on the walls above it
    for k in range(6):
        a = math.radians(30 + 60 * k)
        x, z = cx + round((R - 1.5) * math.sin(a)), cz + round((R - 1.5) * math.cos(a))
        if c.get(x, GY, z) == "minecraft:spruce_planks":
            c.set(x, GY - 1, z, "minecraft:lantern[hanging=true]")
    charts = [{"color": "yellow", "pattern": "minecraft:circle"}, {"color": "white", "pattern": "minecraft:small_stripes"},
              {"color": "black", "pattern": "minecraft:border"}]
    for k in (1, 3, 5, 7):
        a = math.radians(45 * k)
        x, z = cx + round((R - 1) * math.sin(a)), cz + round((R - 1) * math.cos(a))
        f = toward(cx - x, cz - z)
        c.set(x, GY + 3, z, f"minecraft:blue_wall_banner[facing={f}]", {"patterns": charts})
    for (x, z) in ((cx - 3, cz + 3), (cx + 3, cz + 3)):
        c.set(x, GY + 1, z, "minecraft:lantern[hanging=false]") if c.get(x, GY, z) == "minecraft:spruce_planks" else None

    # ---- Hester's cottage (east), half-timbered, with a chimney
    X0, X1, Z0, Z1 = cx + 5, cx + 12, cz - 3, cz + 3
    for x in range(X0, X1 + 1):
        for z in range(Z0 - 1, Z1 + 2):
            if d(x, z) < R + 0.5:
                continue
            c.set(x, 0, z, "minecraft:spruce_planks" if Z0 < z < Z1 and x < X1 else "minecraft:stone_bricks")
            c.set(x, -1, z, "minecraft:stone_bricks")
            if Z0 - 1 < z < Z1 + 1:
                edge = x == X1 or z in (Z0, Z1)
                for y in range(1, 6):
                    if not edge:
                        c.set(x, y, z, "minecraft:air")
                        continue
                    corner = x == X1 and z in (Z0, Z1) or (z in (Z0, Z1) and x in (X0 + 2, X0 + 5))
                    if y == 1:
                        b = "minecraft:stone_bricks"
                    elif corner:
                        b = "minecraft:stripped_dark_oak_log[axis=y]"
                    elif y == 4:
                        b = "minecraft:stripped_dark_oak_log[axis=x]" if z in (Z0, Z1) else "minecraft:stripped_dark_oak_log[axis=z]"
                    else:
                        b = "minecraft:calcite"
                    c.set(x, y, z, b)
    for (x, y, z) in ((X1, 2, cz - 1), (X1, 3, cz - 1), (X1, 2, cz + 1), (X1, 3, cz + 1), (X0 + 4, 2, Z0), (X0 + 4, 3, Z0),
                      (X0 + 4, 2, Z1), (X0 + 4, 3, Z1)):
        c.set(x, y, z, "minecraft:glass_pane")
    # the gable roof (dark tiles), ridge along x, gable end filled in
    for x in range(X0, X1 + 2):
        for i, z0 in enumerate(range(Z0 - 1, cz)):
            y = 4 + i
            zs = (z0, 2 * cz - z0)
            for k, z in enumerate(zs):
                if d(x, z) < R + 0.5 and y <= TOP:
                    continue
                f = "south" if k == 0 else "north"
                c.set(x, y, z, f"minecraft:deepslate_tile_stairs[facing={f},half=bottom]")
                if x == X1 and Z0 < z < Z1:
                    for yy in range(5, y):
                        c.set(x, yy, z, "minecraft:spruce_planks")
                elif Z0 < z < Z1 and x < X1:
                    for yy in range(5, y):
                        c.set(x, yy, z, "minecraft:air")
        y = 4 + (cz - (Z0 - 1))
        if d(x, cz) >= R + 0.5:
            c.set(x, y, cz, "minecraft:deepslate_tile_slab[type=bottom]")
            if x == X1:
                for yy in range(5, y):
                    c.set(x, yy, cz, "minecraft:spruce_planks")
            elif x < X1:
                for yy in range(5, y):
                    c.set(x, yy, cz, "minecraft:air")
    c.set(X1, 6, cz, "minecraft:glass_pane")
    # the chimney, with a fire that smokes
    chx, chz = X1 - 1, Z1
    for y in range(1, 10):
        c.set(chx, y, chz, "minecraft:cobblestone" if y % 3 else "minecraft:mossy_cobblestone")
    c.set(chx, 10, chz, "minecraft:campfire[lit=true,facing=north,signal_fire=false]")
    # inside: her bed, a desk, a chest, a stove
    c.set(X1 - 1, 1, Z0 + 1, "minecraft:red_bed[facing=west,part=head]")
    c.set(X1 - 2, 1, Z0 + 1, "minecraft:red_bed[facing=west,part=foot]")
    c.set(X1 - 1, 1, Z1 - 1, "minecraft:smoker[facing=west,lit=false]")
    c.set(X1 - 2, 1, Z1 - 1, "minecraft:barrel[facing=up]", {"Items": []})
    c.set(X0 + 3, 1, Z1 - 1, "minecraft:crafting_table")
    c.set(X1 - 1, 1, cz, "minecraft:chest[facing=west]", {"Items": []})
    c.set(X0 + 3, 1, Z0 + 1, "minecraft:brewing_stand")
    c.set(X0 + 4, 4, cz, "minecraft:lantern[hanging=true]")
    for x in range(X0 + 2, X1 - 1):
        c.set(x, 1, cz, "minecraft:red_carpet") if c.get(x, 1, cz) in (None, "minecraft:air") else None

    # a woodpile under the eaves, a couple of barrels, pots by the door
    for x in range(X0 + 3, X0 + 6):
        for y in (1, 2):
            c.set(x, y, Z1 + 1, "minecraft:spruce_log[axis=x]" if (x + y) % 3 else "minecraft:stripped_spruce_log[axis=x]")
    c.set(X1 - 1, 1, Z1 + 1, "minecraft:barrel[facing=north]")
    c.set(X1, 1, Z1 + 1, "minecraft:barrel[facing=up]")
    for x in (cx - 2, cx + 2):
        c.set(x, 1, dz + 1, "minecraft:potted_fern" if x < cx else "minecraft:potted_spruce_sapling")

    # ---- the path to the door, and lamps either side
    for z in range(dz + 4, dz + 9):
        for x in range(cx - 1, cx + 2):
            c.set(x, -1, z, "minecraft:dirt_path" if (x == cx or rng.random() < 0.7) else "minecraft:coarse_dirt")
    for x in (cx - 2, cx + 2):
        c.set(x, 1, dz + 3, "minecraft:spruce_fence")
        c.set(x, 2, dz + 3, "minecraft:spruce_fence")
        c.set(x, 3, dz + 3, "minecraft:lantern[hanging=false]")

    # the astronomer (the story's Hester Quill takes her place)
    c.entity(cx - 2 + 0.5, 1, cz + 2 + 0.5, {"id": "minecraft:villager", "PersistenceRequired": Byte(1),
                                             "VillagerData": {"profession": "minecraft:cartographer", "level": 3, "type": "minecraft:plains"},
                                             "Rotation": [Float(180.0), Float(0.0)]})
    return c


# ------------------------------------------------------------------ the moon: shared pieces
def _vnoise(seed, scale):
    """smooth value noise in [-1, 1] (bilinear over a grid of `scale` blocks)"""
    rng = random.Random(seed)
    grid = {}

    def g(i, j):
        if (i, j) not in grid:
            grid[(i, j)] = rng.uniform(-1, 1)
        return grid[(i, j)]

    def f(x, z):
        fx, fz = x / scale, z / scale
        i, j = math.floor(fx), math.floor(fz)
        tx, tz = fx - i, fz - j
        tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
        a = g(i, j) * (1 - tx) + g(i + 1, j) * tx
        b = g(i, j + 1) * (1 - tx) + g(i + 1, j + 1) * tx
        return a * (1 - tz) + b * tz
    return f


def _ellipsoid(cells, centre, radii):
    (x0, y0, z0), (rx, ry, rz) = centre, radii
    for x in range(math.floor(x0 - rx) - 1, math.ceil(x0 + rx) + 2):
        for y in range(math.floor(y0 - ry) - 1, math.ceil(y0 + ry) + 2):
            for z in range(math.floor(z0 - rz) - 1, math.ceil(z0 + rz) + 2):
                if ((x - x0) / rx) ** 2 + ((y - y0) / ry) ** 2 + ((z - z0) / rz) ** 2 <= 1.0:
                    cells.add((x, y, z))


def _capsule(cells, a, b, r0, r1, squash=0.85):
    n = int(math.dist(a, b) * 2) + 1
    for i in range(n + 1):
        t = i / n
        p = tuple(a[k] + (b[k] - a[k]) * t for k in range(3))
        r = r0 + (r1 - r0) * t
        _ellipsoid(cells, p, (r, r * squash, r))


def _ground(c, x, top, rng, z, depth=4, surface=None):
    for y in range(top - depth, top + 1):
        if y == top:
            b = surface or rng.choice(["rocketnautics:lunar_regolith[axis=y]"] * 5 + ["rocketnautics:lunar_loose_regolith"] * 3
                                      + ["rocketnautics:lunar_shattered_regolith"])
        elif y >= top - 1:
            b = "rocketnautics:lunar_regolith[axis=y]"
        else:
            b = "rocketnautics:lunar_rock[axis=y]"
        c.set(x, y, z, b)


def _kneeler(c, x, y, z, yaw, seed):
    """a Faithful pilgrim in gold, kneeling: half sunk in the dust, bowed, hands together"""
    r = random.Random(seed)
    c.entity(x, y - 0.55, z, {
        "id": "minecraft:armor_stand", "ShowArms": Byte(1), "NoBasePlate": Byte(1), "NoGravity": Byte(1),
        "Rotation": [Float(yaw), Float(0.0)],
        "Pose": {"Head": [Float(28.0 + r.uniform(-6, 10)), Float(r.uniform(-8, 8)), Float(0.0)],
                 "Body": [Float(14.0), Float(0.0), Float(0.0)],
                 "LeftArm": [Float(-72.0), Float(18.0), Float(0.0)], "RightArm": [Float(-72.0), Float(-18.0), Float(0.0)],
                 "LeftLeg": [Float(-8.0), Float(0.0), Float(0.0)], "RightLeg": [Float(-8.0), Float(0.0), Float(0.0)]},
        "ArmorItems": [{"id": "minecraft:golden_boots", "count": 1}, {"id": "minecraft:golden_leggings", "count": 1},
                       {"id": "minecraft:golden_chestplate", "count": 1}, {"id": "minecraft:golden_helmet", "count": 1}]})


def _yaw_to(x, z, tx, tz):
    return math.degrees(math.atan2(-(tx - x), tz - z))


EYE_BANNER = {"patterns": [{"color": "black", "pattern": "minecraft:circle"}, {"color": "black", "pattern": "minecraft:border"}]}


# ------------------------------------------------------------------ the Faithful's wreck (on the moon)
def wreck():
    """Their crude gold rocket came in low from the west, ploughed a trench and broke in two: the nose half is driven into
    the dust at the end of the trench, the tail half (fins, burnt engine) lies tumbled beside it, open at the break, the
    logbook's barrel inside. Debris everywhere. Their banner (a black sun in gold) planted on a pole, and the last
    pilgrims kneeling before it, half sunk in the dust."""
    c = Canvas()
    rng = _r(23)
    W, D = 40, 30
    CX, CZ = 20, 15
    noise = _vnoise(5, 5)

    def rr(x, z):
        return math.hypot((x - CX) / 20.0, (z - CZ) / 15.0)

    # ---- the ground: a trench ploughed from the west, a berm either side, a heap where the nose went in
    def floor(x, z):
        h = -1 + 0.35 * noise(x, z)
        zc = 15 + (x - 4) * 0.08
        if 3 <= x <= 31:
            t = (x - 3) / 28.0
            w = 1.6 + 2.2 * t
            depth = 2.6 * (t * t * (3 - 2 * t))
            dz = abs(z - zc)
            if dz < w:
                h -= depth * (1 - (dz / w) ** 2)
            elif dz < w + 2.5:
                h += 0.9 * t * (1 - (dz - w) / 2.5)
        h += 2.2 * max(0.0, 1 - math.hypot(x - 33, z - 17.5) / 4.5)
        return int(round(h))
    F = {}
    for x in range(0, W + 1):
        for z in range(0, D + 1):
            if rr(x, z) <= 1.08:
                F[(x, z)] = floor(x, z)
                trench = 3 <= x <= 31 and abs(z - (15 + (x - 4) * 0.08)) < 1.6 + 2.2 * (x - 3) / 28.0
                surf = None
                if trench and rng.random() < 0.55:
                    surf = rng.choice(["rocketnautics:lunar_shattered_regolith", "rocketnautics:lunar_fragmented_rock",
                                       "minecraft:smooth_basalt", "minecraft:blackstone"])
                _ground(c, x, F[(x, z)], rng, z, surface=surf)
                for y in range(F[(x, z)] + 1, 12):
                    c.set(x, y, z, "minecraft:air")

    hull_mats = ["minecraft:gold_block"] * 18 + ["minecraft:raw_gold_block"]

    def tube(a, b, r_of_t, mat_of, open_end=None, jag=None):
        """a hollow tube (hull) from a to b; r_of_t(t) radius at t in [0, 1]"""
        ax, ay, az = a
        L = math.dist(a, b)
        u = tuple((b[k] - a[k]) / L for k in range(3))
        lo = [math.floor(min(a[k], b[k])) - 4 for k in range(3)]
        hi = [math.ceil(max(a[k], b[k])) + 4 for k in range(3)]
        for x in range(lo[0], hi[0] + 1):
            for y in range(lo[1], hi[1] + 1):
                for z in range(lo[2], hi[2] + 1):
                    v = (x - ax, y - ay, z - az)
                    s = sum(v[k] * u[k] for k in range(3))
                    if s < 0 or s > L:
                        continue
                    perp = math.sqrt(max(0.0, sum(q * q for q in v) - s * s))
                    t = s / L
                    r = r_of_t(t)
                    if jag and jag(t, x, y, z):
                        continue
                    if r - 1.0 <= perp <= r + 0.15:
                        c.set(x, y, z, mat_of(t, x, y, z, perp))
                    elif perp < r - 1.0:
                        c.set(x, y, z, "minecraft:air")

    # the nose half, nosed into the heap at the end of the trench
    A, B = (22.0, 7.2, 16.4), (32.5, -1.8, 17.8)

    def nose_r(t):
        return 2.5 if t < 0.55 else max(0.4, 2.5 * (1 - (t - 0.55) / 0.45) ** 0.8)

    def nose_mat(t, x, y, z, perp):
        if abs(t - 0.3) < 0.04 or abs(t - 0.55) < 0.04:
            return "minecraft:chiseled_polished_blackstone"
        if 0.12 < t < 0.5 and z >= 18 and abs(y - (7.2 - 9.0 * t)) < 0.8 and int(t * 20) % 3 == 0:
            return "minecraft:tinted_glass"                       # portholes
        if t > 0.85:
            return "minecraft:raw_gold_block"
        return rng.choice(hull_mats)
    tube(A, B, nose_r, nose_mat, jag=lambda t, x, y, z: t < 0.08 and rng.random() < 0.55)
    # the tail half, tumbled on its side behind it, open at the break
    C, Dt = (18.0, 2.4, 13.0), (5.5, 2.6, 8.2)

    def tail_mat(t, x, y, z, perp):
        if abs(t - 0.35) < 0.04 or abs(t - 0.7) < 0.04:
            return "minecraft:chiseled_polished_blackstone"
        if t < 0.08:
            return rng.choice(["minecraft:gilded_blackstone", "minecraft:raw_gold_block", "minecraft:gold_block"])
        if t > 0.93:
            return "minecraft:polished_blackstone"
        return rng.choice(hull_mats)
    tube(C, Dt, lambda t: 2.5, tail_mat, jag=lambda t, x, y, z: t < 0.07 and rng.random() < 0.6)
    # the engine bell: a flared, scorched cone behind the tail
    L = math.dist(C, Dt)
    u = tuple((Dt[k] - C[k]) / L for k in range(3))
    E0 = Dt
    E1 = tuple(Dt[k] + u[k] * 3.2 for k in range(3))
    tube(E0, E1, lambda t: 1.8 + 1.3 * t, lambda t, x, y, z, p: rng.choice(["minecraft:polished_blackstone", "minecraft:blackstone",
                                                                             "minecraft:smooth_basalt"]))
    # the fins: big gold blades at the tail. Two stand up in the air, one is driven into the dust, one was torn off
    side = (-u[2], 0.0, u[0])
    up = (0.0, 1.0, 0.0)
    for k, (vs, vu) in enumerate(((0.0, 1.0), (1.0, 0.0), (-1.0, 0.0), (0.0, -1.0))):
        dn = math.hypot(vs, vu)
        d = tuple((side[i] * vs + up[i] * vu) / dn for i in range(3))
        s0 = L - 7.0
        s_ = s0
        while s_ <= L + 0.6:
            ext = (5.2 if vu > 0.5 else 3.6) * min(1.0, (s_ - s0) / 6.0)
            q = 2.0
            while q <= 2.4 + ext:
                p = tuple(C[i] + u[i] * s_ + d[i] * q for i in range(3))
                x, y, z = (int(math.floor(p[0] + 0.5)), int(math.floor(p[1] + 0.5)), int(math.floor(p[2] + 0.5)))
                if y > F.get((x, z), -9):
                    edge = q > 1.9 + ext
                    c.set(x, y, z, "minecraft:polished_blackstone" if edge else "minecraft:gold_block")
                q += 0.35
            s_ += 0.35
    for (x, y, z) in ((11, 0, 19), (12, 0, 19), (12, 0, 20), (13, 0, 20), (13, 0, 21), (12, 1, 20), (13, 1, 21)):    # the torn fin
        c.set(x, max(y, F.get((x, z), -1) + 1 + y), z, "minecraft:gold_block" if y == 0 else "minecraft:polished_blackstone")
    # a rupture in the tail's flank: the plating peeled back, the ribs showing
    for s_ in range(5, 8):
        for ang in range(75, 135, 15):
            a = math.radians(ang)
            p = tuple(C[i] + u[i] * s_ + (side[i] * math.cos(a) + up[i] * math.sin(a)) * 2.0 for i in range(3))
            x, y, z = (int(math.floor(p[0] + 0.5)), int(math.floor(p[1] + 0.5)), int(math.floor(p[2] + 0.5)))
            c.set(x, y, z, "minecraft:polished_blackstone_wall" if s_ % 2 == 0 else "minecraft:air")
    # inside the tail: the logbook's barrel, a lantern gone dark, a cot
    c.set(16, 1, 13, "minecraft:barrel[facing=up]", {"Items": []})
    c.set(15, 1, 13, "minecraft:chain[axis=x]")
    c.set(13, 1, 12, "minecraft:black_carpet")
    # debris and scorching along the trench
    for i in range(26):
        x = rng.randint(4, 34)
        z = int(15 + (x - 4) * 0.08 + rng.uniform(-6, 6))
        if (x, z) in F and c.get(x, F[(x, z)] + 1, z) == "minecraft:air":
            c.set(x, F[(x, z)] + 1, z, rng.choice(["minecraft:gold_block", "minecraft:raw_gold_block", "minecraft:gilded_blackstone",
                                                   "minecraft:polished_blackstone_slab[type=bottom]", "minecraft:chain[axis=x]",
                                                   "minecraft:chain[axis=z]", "minecraft:iron_trapdoor[facing=north,half=bottom,open=false]"]))
    # ---- the banner, and the pilgrims kneeling before it
    bx, bz = 10, 24
    by = F[(bx, bz)]
    for y in range(by + 1, by + 3):
        c.set(bx, y, bz, "minecraft:polished_blackstone_wall")
    c.set(bx, by + 3, bz, "minecraft:yellow_banner[rotation=4]", EYE_BANNER)
    spots = ((14, 22), (15, 25), (13, 27), (7, 21), (16, 19))
    for i, (x, z) in enumerate(spots):
        _kneeler(c, x + 0.5, F[(x, z)] + 1, z + 0.5, _yaw_to(x, z, bx, bz), 100 + i)
    for (x, z) in ((6, 25), (12, 28)):         # two who didn't finish their prayers
        c.set(x, F[(x, z)] + 1, z, f"minecraft:skeleton_skull[rotation={rng.randint(0, 15)}]")
    return c


# ------------------------------------------------------------------ Iskar's rest (on the moon)
def iskar():
    """The Silver King fell, and made the great crater. He lies in it sprawled on his back, sunk in the dust: a silver
    crown, a face turned to the sky with its mouth open, a burn through his chest like a black sun ringed in gold, his
    left hand flung open, his right still closed on the Eclipse: its gold ring of a guard standing up out of the dust, the
    black blade chained down where the Faithful tried to break it. Their altar by his head, their scaffold at his side."""
    c = Canvas()
    rng = _r(37)
    CX, CZ, RX, RZ = 40, 30, 40, 30
    DEPTH = 5.0
    TOP = 9
    noise = _vnoise(9, 6)
    fine = _vnoise(10, 3)

    def rr(x, z):
        return math.hypot((x - CX) / RX, (z - CZ) / RZ)

    def floor_h(x, z):
        r = rr(x, z)
        h = -1.0
        if r < 0.86:
            h -= DEPTH * (1 - (r / 0.86) ** 2)
        h += 2.4 * math.exp(-((r - 0.95) / 0.09) ** 2)        # the rim
        h += 0.6 * noise(x, z) + 0.3 * fine(x, z)
        return h
    F = {(x, z): floor_h(x, z) for x in range(-2, 2 * CX + 3) for z in range(-2, 2 * CZ + 3) if rr(x, z) <= 1.16}

    def fl(x, z):
        return F.get((round(x), round(z)), -1.0)

    # ---- the body (each part's height follows the crater floor under it)
    body = set()
    zc = CZ

    def at(x, lift, z):
        return (x, fl(x, z) + lift, z)
    HX = 13                                                  # the head's centre
    _ellipsoid(body, at(HX, 1.4, zc), (5.4, 4.6, 4.9))       # the skull
    _ellipsoid(body, at(HX + 4.2, 0.6, zc), (2.6, 3.4, 3.4))   # jaw and chin
    _capsule(body, at(HX + 5, 0.5, zc), at(HX + 9, 0.6, zc), 2.7, 2.9)    # neck
    _ellipsoid(body, at(29, 1.4, zc), (8.5, 4.6, 9.6))       # chest
    _ellipsoid(body, at(38, 1.0, zc), (6.5, 3.9, 7.6))       # belly
    _ellipsoid(body, at(44, 0.9, zc), (4.8, 3.6, 7.4))       # hips
    for sz in (zc - 9.5, zc + 9.5):
        _ellipsoid(body, at(23.5, 1.3, sz), (3.6, 3.4, 3.4))  # shoulders
    # left arm flung out, hand open, palm up
    _capsule(body, at(24, 0.9, zc - 10), at(29, 0.7, zc - 18.5), 2.7, 2.3)
    _capsule(body, at(29, 0.7, zc - 18.5), at(25.5, 0.7, zc - 25), 2.3, 1.9)
    LH = (24.5, zc - 26.5)
    _ellipsoid(body, at(LH[0], 0.5, LH[1]), (2.4, 1.5, 2.2))
    for k, (fx, fz) in enumerate(((-2.8, -1.4), (-1.2, -2.6), (0.6, -2.9), (2.2, -2.0))):
        _capsule(body, at(LH[0] + fx * 0.6, 0.6, LH[1] + fz * 0.6), at(LH[0] + fx * 1.25, 0.5 + (k % 2) * 0.4, LH[1] + fz * 1.25), 0.85, 0.7, 1.0)
    _capsule(body, at(LH[0] + 1.6, 0.5, LH[1] + 1.4), at(LH[0] + 3.4, 0.9, LH[1] + 0.8), 0.9, 0.75, 1.0)   # thumb
    # right arm down by his side, the fist closed on the Eclipse
    _capsule(body, at(24, 0.9, zc + 10), at(32, 0.7, zc + 15.5), 2.7, 2.3)
    _capsule(body, at(32, 0.7, zc + 15.5), at(39, 0.8, zc + 18), 2.3, 2.0)
    FIST = (41.0, zc + 18.6)
    _ellipsoid(body, at(FIST[0], 1.0, FIST[1]), (2.5, 2.3, 2.4))
    # legs: the right knee a little raised; feet turned up to the sky
    _capsule(body, at(45, 0.8, zc - 4.2), at(55, 0.9, zc - 5.5), 3.5, 2.9)
    _capsule(body, at(55, 0.9, zc - 5.5), at(65, 0.8, zc - 6.5), 2.9, 2.2)
    _capsule(body, at(45, 0.8, zc + 4.2), at(54, 2.6, zc + 6.5), 3.5, 3.0)
    _capsule(body, at(54, 2.6, zc + 6.5), at(64, 0.8, zc + 7.5), 3.0, 2.2)
    for fz in (zc - 6.6, zc + 7.6):
        _ellipsoid(body, at(67, 2.4, fz), (1.9, 3.6, 2.3))
        _ellipsoid(body, at(68.2, 4.4, fz), (1.2, 1.6, 2.0))  # the toes

    # ---- the ground: the bowl, the rim, dust drifted against him (the ground runs over whatever of him it covers)
    near = set()
    for (x, y, z) in body:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                near.add((x + dx, z + dz))
    tops = {}
    for (x, y, z) in body:
        tops[(x, z)] = max(tops.get((x, z), -99), y)
    for (x, y, z) in body:
        c.set(x, y, z, _pale(rng))
    for (x, z), h in F.items():
        top = int(round(h)) + (1 if (x, z) in near and (x, z) not in tops else 0)
        _ground(c, x, top, rng, z, depth=4)
        for y in range(top + 1, TOP):
            if (x, y, z) not in body:
                c.set(x, y, z, "minecraft:air")

    def top_of(x, z):
        return tops.get((x, z))

    # ---- the face, turned to the sky
    def carve(x, z, depth, floor_block):
        t = top_of(x, z)
        if t is None:
            return
        for y in range(t - depth + 1, t + 1):
            c.set(x, y, z, "minecraft:air")
        c.set(x, t - depth, z, floor_block)
    for ez in (zc - 2, zc + 1):                     # the eyes
        for dx in (0, 1):
            for dz in (0, 1):
                carve(HX - 1 + dx, ez + dz, 1, "minecraft:black_concrete")
    for z in range(zc - 3, zc + 4):                 # the brow
        t = top_of(HX - 2, z)
        if t is not None:
            c.set(HX - 2, t + 1, z, "minecraft:smooth_quartz")
    for x in range(HX, HX + 3):                     # the nose
        t = top_of(x, zc)
        if t is not None:
            c.set(x, t + 1, zc, "minecraft:calcite")
    for x in (HX + 4, HX + 5):                      # the mouth, open (the Last Light lives in there)
        for z in range(zc - 1, zc + 2):
            carve(x, z, 2, "minecraft:black_concrete")
    c.set(HX + 4, top_of(HX + 4, zc) - 1 if top_of(HX + 4, zc) else 0, zc, "minecraft:sea_lantern")
    # ---- the silver crown: a great crescent diadem, slipped from his brow, lying in the dust round his head, its
    #      tines standing up
    ccx, ccz = HX - 1.5, zc
    for x in range(int(ccx) - 10, int(ccx) + 10):
        for z in range(int(ccz) - 10, int(ccz) + 11):
            d_out = math.hypot(x - ccx, z - ccz)
            d_in = math.hypot(x - (ccx + 3.2), z - ccz)
            if d_out <= 8.6 and d_in >= 7.0 and x <= HX + 3:
                t = int(round(fl(x, z)))
                top = tops.get((x, z))
                base = (top if top is not None else t) + 1
                c.set(x, base, z, "minecraft:iron_block" if (x + z) % 4 else "minecraft:polished_diorite")
                if d_out > 7.6 and (round(math.degrees(math.atan2(z - ccz, x - ccx))) % 30) < 10:
                    c.set(x, base + 1, z, "minecraft:iron_block")
                    c.set(x, base + 2, z, "minecraft:end_rod[facing=up]")
    # ---- his beard, silver, flowing from his chin down over his chest
    for x in range(HX + 6, HX + 14):
        w = 3.2 - (x - (HX + 6)) * 0.3
        for z in range(zc - 4, zc + 5):
            if abs(z - zc) <= w:
                t = tops.get((x, z))
                if t is not None:
                    c.set(x, t + 1, z, "minecraft:white_wool" if (x + z) % 3 else "minecraft:light_gray_wool")
                    if abs(z - zc) < w - 1.5 and x < HX + 11:
                        c.set(x, t + 2, z, "minecraft:white_wool")
    # ---- where the sun went through him: a black sun ringed with gold (his chest), and two smaller burns
    for (bx, bz, br, hole) in ((30, zc - 1, 6.5, True), (55, zc + 6, 3.0, False), (23, zc + 9, 2.4, False)):
        for x in range(int(bx - br) - 1, int(bx + br) + 2):
            for z in range(int(bz - br) - 1, int(bz + br) + 2):
                t = top_of(x, z)
                if t is None:
                    continue
                dd = math.hypot(x - bx, z - bz)
                if dd < br - 2.0:
                    if hole and dd < br - 3.0:
                        for y in range(t - 2, t + 1):
                            c.set(x, y, z, "minecraft:air")
                        c.set(x, t - 3, z, rng.choice(["minecraft:obsidian", "minecraft:blackstone", "minecraft:crying_obsidian",
                                                       "minecraft:obsidian"]))
                    else:
                        c.set(x, t, z, rng.choice(["minecraft:blackstone", "minecraft:obsidian", "minecraft:basalt[axis=y]",
                                                   "minecraft:polished_blackstone"]))
                elif dd < br:
                    c.set(x, t, z, rng.choice(["minecraft:gold_block", "minecraft:gold_block", "minecraft:raw_gold_block",
                                               "minecraft:gilded_blackstone"]))
        if hole:     # his ribs, across the hole
            for x in range(int(bx - br) + 3, int(bx + br) - 2, 2):
                for z in range(int(bz - br) + 2, int(bz + br) - 1):
                    t = top_of(x, z)
                    if t is not None and math.hypot(x - bx, z - bz) < br - 2.2:
                        c.set(x, t, z, "minecraft:bone_block[axis=z]")

    # ---- the Eclipse, still in his fist: the grip through his hand, the guard a gold ring standing out of the dust,
    #      the black blade lying along the crater floor, chained down
    ux, uz = 0.97, 0.24
    vx, vz = -uz, ux
    gx, gz = FIST[0] + 3.4 * ux, FIST[1] + 3.4 * uz
    gy = fl(gx, gz) + 2.6
    for i in range(-6, 1):                                         # grip and pommel
        x, z = FIST[0] + i * ux * 0.8, FIST[1] + i * uz * 0.8
        y = fl(x, z) + 1.4
        b = "minecraft:gold_block" if i <= -5 else "minecraft:polished_blackstone"
        ix, iy, iz = int(round(x)), int(round(y)), int(round(z))
        if c.get(ix, iy, iz) in (None, "minecraft:air") or i <= -5:
            c.set(ix, iy, iz, b)
    for a in range(0, 360, 6):                                     # the guard: a black sun in a ring of gold
        for rad in (2.4, 3.2, 4.0, 4.6):
            px = gx + vx * rad * math.cos(math.radians(a))
            pz = gz + vz * rad * math.cos(math.radians(a))
            py = gy + rad * math.sin(math.radians(a))
            ix, iy, iz = int(round(px)), int(round(py)), int(round(pz))
            if py < fl(px, pz) - 0.5:
                continue
            c.set(ix, iy, iz, "minecraft:gold_block" if rad > 2.8 else "minecraft:black_concrete")
    for a in range(0, 360, 10):
        for rad in (0.0, 1.0, 1.7, 2.3):
            px = gx + vx * rad * math.cos(math.radians(a))
            pz = gz + vz * rad * math.cos(math.radians(a))
            py = gy + rad * math.sin(math.radians(a))
            if py >= fl(px, pz) - 0.5:
                c.set(int(round(px)), int(round(py)), int(round(pz)), "minecraft:black_concrete")
    BL = 23
    for i in range(1, BL + 1):                                     # the blade
        half = 1.7 if i < BL - 5 else 1.7 * (BL - i) / 5.0 + 0.2
        cxl, czl = gx + ux * i, gz + uz * i
        base_y = int(round(fl(cxl, czl))) + 1
        for w in range(-2, 3):
            if abs(w) > half + 0.3:
                continue
            x, z = int(round(cxl + vx * w)), int(round(czl + vz * w))
            if abs(w) >= 2 or (abs(w) >= 1 and half < 1.2):
                b = "minecraft:smooth_basalt"
            elif w == 0:
                b = "minecraft:crying_obsidian" if i % 5 == 0 else "minecraft:obsidian"
            else:
                b = "minecraft:black_concrete"
            c.set(x, base_y, z, b)
            if w == 0 and i < BL - 3:
                c.set(x, base_y + 1, z, "minecraft:polished_blackstone_slab[type=bottom]")
    for i in (6, 12, 18):                                          # the Faithful's chains, staked down over it
        cxl, czl = gx + ux * i, gz + uz * i
        base_y = int(round(fl(cxl, czl))) + 1
        for w in range(-4, 5):
            x, z = int(round(cxl + vx * w)), int(round(czl + vz * w))
            if abs(w) == 4:
                for y in range(int(round(fl(x, z))) + 1, base_y + 3):
                    c.set(x, y, z, "minecraft:polished_blackstone_wall")
            else:
                c.set(x, base_y + 2, z, "minecraft:chain[axis=z]")
    # ---- the Faithful's altar by his head, and their scaffold at his side, with the chips they took out of him
    ax, az = 3, zc
    ay = int(round(fl(ax, az)))
    for dx in (-1, 0, 1):
        for dz in (-2, -1, 0, 1, 2):
            c.set(ax + dx, ay + 1, az + dz, "minecraft:polished_blackstone_bricks")
    c.set(ax, ay + 2, az, "minecraft:gilded_blackstone")
    c.set(ax, ay + 3, az, "minecraft:gold_block")
    for dz in (-2, 2):
        c.set(ax, ay + 2, az + dz, "minecraft:yellow_candle[candles=4,lit=false]")
    c.set(ax - 1, ay + 2, az, "minecraft:yellow_banner[rotation=12]", EYE_BANNER)
    for y in range(4):
        for (x, z) in ((31, zc - 11), (32, zc - 11)):
            yy = int(round(fl(x, z))) + 1 + y
            c.set(x, yy, z, "minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]")
    for i in range(14):
        x, z = 26 + rng.randint(0, 12), zc - 12 - rng.randint(0, 4)
        t = int(round(fl(x, z)))
        if c.get(x, t + 1, z) == "minecraft:air":
            c.set(x, t + 1, z, rng.choice(["minecraft:calcite", "minecraft:diorite", "minecraft:calcite_slab" if False else "minecraft:smooth_quartz_slab[type=bottom]"]))
    return c


def _pale(rng):
    r = rng.random()
    if r < 0.62:
        return "minecraft:calcite"
    if r < 0.88:
        return "minecraft:smooth_quartz"
    if r < 0.94:
        return "minecraft:diorite"
    return "minecraft:bone_block[axis=y]"


BUILDERS = {"observatory": observatory, "faithful_wreck": wreck, "iskars_rest": iskar}


def lowest(name):
    """the lowest layer of a build (y = 0 is the first air above the ground)"""
    return min(p[1] for p in BUILDERS[name]().blocks)


def size(name):
    """[x, y, z] size of a build's template"""
    ps = BUILDERS[name]().blocks
    return [max(p[i] for p in ps) - min(p[i] for p in ps) + 1 for i in range(3)]


def start_height(name):
    """what the structure's start_height must be for its y = 0 to land on the first air: jigsaw placement puts the
    bottom layer at the surface's first air + start_height - 1 (the piece's ground level is one up from its bottom)"""
    return lowest(name) + 1


def write_all(struct_dir):
    import os
    os.makedirs(struct_dir, exist_ok=True)
    out = {}
    for name, fn in BUILDERS.items():
        out[name] = fn().save(os.path.join(struct_dir, f"{name}.nbt"))
    return out   # name -> (size, lowest y): the lowest y says how far down to sink it
