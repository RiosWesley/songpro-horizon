"""Where payload.bin's bytes sit inside a DiLink 3.0 package (BYDUpdatePackage/msm8953_64/UpdateFull.zip ->
update.zip -> payload.bin, all stored): prints its offset, size and magic, for extract_payload.py.

    python locate_payload.py Di3.0_13.1.33.<build>.zip
"""
import struct, sys, zipfile

def data_start(f, base, info):
    f.seek(base + info.header_offset)
    hdr = f.read(30)
    assert hdr[:4] == b"PK\x03\x04", "no local header"
    name_len, extra_len = struct.unpack("<HH", hdr[26:30])
    return base + info.header_offset + 30 + name_len + extra_len

path = sys.argv[1]
f = open(path, "rb")
outer = zipfile.ZipFile(f)
i1 = outer.getinfo("BYDUpdatePackage/msm8953_64/UpdateFull.zip")
s1 = data_start(f, 0, i1)
inner = zipfile.ZipFile(outer.open(i1))
i2 = inner.getinfo("update.zip")
s2 = data_start(f, s1, i2)
upd = zipfile.ZipFile(inner.open(i2))
i3 = upd.getinfo("payload.bin")
s3 = data_start(f, s2, i3)
assert i1.compress_type == i2.compress_type == i3.compress_type == 0
f.seek(s3)
print(s3, i3.file_size, f.read(4))
