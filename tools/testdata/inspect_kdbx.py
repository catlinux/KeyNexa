#!/usr/bin/env python3
"""Imprime versión de KDBX, cifrado y KDF de cada archivo .kdbx indicado (solo lee la cabecera)."""
import struct
import sys
import uuid

CIPHERS = {
    "31c1f2e6-bf71-4350-be58-05216afc5aff": "AES-256",
    "d6038a2b-8b6f-4cb5-a524-339a31dbb59a": "ChaCha20",
    "ad68f29f-576f-4bb9-a36a-d47af965346c": "Twofish",
}
KDFS = {
    "c9d9f39a-628a-4460-bf74-0d08c18a4fea": "AES-KDF",
    "7c02bb82-79a7-4ac0-927d-114a00648238": "AES-KDF",
    "ef636ddf-8c29-444b-91f7-a9a403e30a0c": "Argon2d",
    "9e298b19-56db-4773-b23d-fc3ec6f0a1e6": "Argon2id",
}


def read_variant_dict(data):
    pos = 2
    out = {}
    while pos < len(data):
        vtype = data[pos]
        pos += 1
        if vtype == 0:
            break
        klen = struct.unpack_from("<i", data, pos)[0]
        pos += 4
        key = data[pos:pos + klen].decode()
        pos += klen
        vlen = struct.unpack_from("<i", data, pos)[0]
        pos += 4
        out[key] = (vtype, data[pos:pos + vlen])
        pos += vlen
    return out


def inspect(path):
    with open(path, "rb") as f:
        raw = f.read(1 << 20)
    sig1, sig2, minor, major = struct.unpack_from("<IIHH", raw, 0)
    if (sig1, sig2) != (0x9AA2D903, 0xB54BFB67):
        return "not a KDBX file"
    pos = 12
    length_fmt = "<H" if major == 3 else "<I"
    length_size = struct.calcsize(length_fmt)
    cipher = kdf = params = None
    while True:
        field = raw[pos]
        size = struct.unpack_from(length_fmt, raw, pos + 1)[0]
        data = raw[pos + 1 + length_size:pos + 1 + length_size + size]
        pos += 1 + length_size + size
        if field == 2:
            cipher = CIPHERS.get(str(uuid.UUID(bytes=data)), str(uuid.UUID(bytes=data)))
        elif field == 11:
            params = read_variant_dict(data)
            kdf_id = str(uuid.UUID(bytes=params["$UUID"][1]))
            kdf = KDFS.get(kdf_id, kdf_id)
        elif field == 0:
            break
    if kdf is None:
        kdf = "AES-KDF"
    detail = ""
    if params and kdf.startswith("Argon2"):
        mem = struct.unpack("<Q", params["M"][1])[0] // (1 << 20)
        iters = struct.unpack("<Q", params["I"][1])[0]
        detail = f" memory={mem}MiB iterations={iters}"
    elif params and kdf == "AES-KDF":
        detail = f" rounds={struct.unpack('<Q', params['R'][1])[0]}"
    return f"KDBX {major}.{minor} cipher={cipher} kdf={kdf}{detail}"


if __name__ == "__main__":
    for p in sys.argv[1:]:
        print(f"{p}: {inspect(p)}")
