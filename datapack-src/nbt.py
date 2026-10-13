"""A tiny NBT writer for structure templates (gzipped, big-endian), and a voxel canvas to draw them on.

Values: dict -> compound, list -> list (element type from the first element), str -> string, bool -> byte,
int -> int, float -> double; wrap with Byte/Float/Long for those types."""
import gzip
import struct


class Byte(int):
    pass


class Float(float):
    pass


class Long(int):
    pass


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _tagtype(v):
    if isinstance(v, bool) or isinstance(v, Byte):
        return 1
    if isinstance(v, Long):
        return 4
    if isinstance(v, int):
        return 3
    if isinstance(v, Float):
        return 5
    if isinstance(v, float):
        return 6
    if isinstance(v, str):
        return 8
    if isinstance(v, list):
        return 9
    if isinstance(v, dict):
        return 10
    raise TypeError(type(v))


def _payload(v):
    t = _tagtype(v)
    if t == 1:
        return struct.pack(">b", int(v))
    if t == 3:
        return struct.pack(">i", v)
    if t == 4:
        return struct.pack(">q", v)
    if t == 5:
        return struct.pack(">f", v)
    if t == 6:
        return struct.pack(">d", v)
    if t == 8:
        return _str(v)
    if t == 9:
        et = _tagtype(v[0]) if v else 0
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(x) for x in v)
    out = b""
    for k, x in v.items():
        out += bytes([_tagtype(x)]) + _str(k) + _payload(x)
    return out + b"\x00"


def write(path, root):
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(bytes([10]) + _str("") + _payload(root))


def state(s):
    """'minecraft:oak_stairs[facing=north,half=top]' -> {'Name': ..., 'Properties': {...}}"""
    if "[" in s:
        name, props = s[:-1].split("[", 1)
        return {"Name": name, "Properties": dict(p.split("=", 1) for p in props.split(","))}
    return {"Name": s}


class Canvas:
    """blocks by position (positions never set are left as they are in the world, like structure voids)"""

    def __init__(self):
        self.blocks = {}
        self.nbt = {}
        self.entities = []

    def set(self, x, y, z, s, nbt=None):
        self.blocks[(x, y, z)] = s
        if nbt is not None:
            self.nbt[(x, y, z)] = nbt
        else:
            self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, s)

    def entity(self, x, y, z, nbt):
        self.entities.append(((x, y, z), nbt))

    def save(self, path, data_version=3955):
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        mx, my, mz = min(xs), min(ys), min(zs)
        size = [max(xs) - mx + 1, max(ys) - my + 1, max(zs) - mz + 1]
        palette, index = [], {}
        blocks = []
        for (x, y, z), s in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if s not in index:
                index[s] = len(palette)
                palette.append(state(s))
            b = {"pos": [x - mx, y - my, z - mz], "state": index[s]}
            if (x, y, z) in self.nbt:
                b["nbt"] = self.nbt[(x, y, z)]
            blocks.append(b)
        ents = []
        for (x, y, z), n in self.entities:
            ents.append({"pos": [float(x - mx), float(y - my), float(z - mz)],
                         "blockPos": [int(x - mx), int(y - my), int(z - mz)], "nbt": n})
        root = {"size": size, "palette": palette, "blocks": blocks, "entities": ents, "DataVersion": data_version}
        if not ents:
            root["entities"] = []
        write(path, root)
        return size, my
