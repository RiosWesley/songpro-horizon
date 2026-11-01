"""Rebuild partition images from an Android A/B full-OTA payload.bin read in place (no copy).

    python extract.py PACKAGE OFFSET list
    python extract.py PACKAGE OFFSET extract OUT_DIR system [vendor ...]

Handles the operations of a full OTA (REPLACE, REPLACE_BZ, REPLACE_XZ, ZERO, DISCARD) one at a time,
so memory stays at one operation's data. Images are written sparse, at their final size.
"""
import bz2, lzma, os, struct, sys


def varint(b, i):
    shift = result = 0
    while True:
        c = b[i]
        i += 1
        result |= (c & 0x7F) << shift
        if c < 0x80:
            return result, i
        shift += 7


def fields(b):
    """Protobuf message -> {field: [values]} (ints for varints, bytes for length-delimited)."""
    out, i = {}, 0
    while i < len(b):
        key, i = varint(b, i)
        num, wire = key >> 3, key & 7
        if wire == 0:
            val, i = varint(b, i)
        elif wire == 2:
            n, i = varint(b, i)
            val, i = b[i:i + n], i + n
        elif wire == 1:
            val, i = b[i:i + 8], i + 8
        elif wire == 5:
            val, i = b[i:i + 4], i + 4
        else:
            raise ValueError("wire type %d" % wire)
        out.setdefault(num, []).append(val)
    return out


def main():
    package, base, cmd = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    f = open(package, "rb")
    f.seek(base)
    magic, version, manifest_size = struct.unpack(">4sQQ", f.read(20))
    assert magic == b"CrAU", magic
    sig_size = struct.unpack(">I", f.read(4))[0] if version >= 2 else 0
    manifest = fields(f.read(manifest_size))
    blobs = base + 20 + (4 if version >= 2 else 0) + manifest_size + sig_size
    block = manifest.get(3, [4096])[0]
    parts = {}
    for raw in manifest.get(13, []):
        p = fields(raw)
        name = p[1][0].decode()
        info = fields(p[7][0]) if 7 in p else {}
        parts[name] = (info.get(1, [0])[0], p.get(8, []))

    if cmd == "list":
        print("payload v%d, block %d" % (version, block))
        for name, (size, ops) in parts.items():
            print("%-14s %12d bytes  %6d ops" % (name, size, len(ops)))
        return

    out_dir, wanted = sys.argv[4], sys.argv[5:]
    os.makedirs(out_dir, exist_ok=True)
    for name in wanted:
        size, ops = parts[name]
        path = os.path.join(out_dir, name + ".img")
        with open(path, "wb") as img:
            img.truncate(size)
            for n, raw in enumerate(ops):
                op = fields(raw)
                kind = op.get(1, [0])[0]
                if kind in (6, 7):  # ZERO, DISCARD: the sparse file already reads as zeros
                    continue
                f.seek(blobs + op.get(2, [0])[0])
                data = f.read(op.get(3, [0])[0])
                if kind == 1:
                    data = bz2.decompress(data)
                elif kind == 8:
                    data = lzma.decompress(data)
                elif kind != 0:
                    raise SystemExit("%s: operation type %d is not a full-OTA one" % (name, kind))
                pos = 0
                for ext in op.get(6, []):
                    e = fields(ext)
                    start, count = e.get(1, [0])[0], e.get(2, [0])[0]
                    img.seek(start * block)
                    img.write(data[pos:pos + count * block])
                    pos += count * block
                if n % 500 == 0:
                    print("%s: %d/%d" % (name, n, len(ops)), flush=True)
        print("wrote", path, size, flush=True)


main()
