#!/usr/bin/env python3
"""Builds app/src/main/assets/basemap.bin from Natural Earth 1:50m GeoJSON (public domain).

    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_land.geojson
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_lakes.geojson
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_boundary_lines_land.geojson
    python3 build_basemap.py . ../../app/src/main/assets/basemap.bin

Format, all integers unsigned LEB128 varints unless noted:
    magic "AMB1"
    layer count
    per layer: kind byte (0 land, 1 lake, 2 border), ring count,
               per ring: point count, then points as zigzag varint deltas of
               (lon * 10^4, lat * 10^4), the first delta taken from (0, 0).
"""
import json
import sys
from pathlib import Path

SCALE = 10_000


def varint(n: int) -> bytes:
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def zigzag(n: int) -> int:
    return (n << 1) ^ (n >> 63)


def rings(geometry):
    t, c = geometry["type"], geometry["coordinates"]
    if t == "Polygon":
        yield from c
    elif t == "MultiPolygon":
        for polygon in c:
            yield from polygon
    elif t == "LineString":
        yield c
    elif t == "MultiLineString":
        yield from c


def encode_layer(kind: int, path: Path) -> bytes:
    data = json.loads(path.read_text())
    all_rings = [r for f in data["features"] for r in rings(f["geometry"]) if len(r) >= 2]
    out = bytearray([kind]) + varint(len(all_rings))
    for ring in all_rings:
        out += varint(len(ring))
        px = py = 0
        for lon, lat, *_ in ring:
            x, y = round(lon * SCALE), round(lat * SCALE)
            out += varint(zigzag(x - px)) + varint(zigzag(y - py))
            px, py = x, y
    return bytes(out)


def main(src: str, dst: str) -> None:
    src_dir = Path(src)
    layers = [
        (0, src_dir / "ne_50m_land.geojson"),
        (1, src_dir / "ne_50m_lakes.geojson"),
        (2, src_dir / "ne_50m_admin_0_boundary_lines_land.geojson"),
    ]
    body = b"AMB1" + varint(len(layers)) + b"".join(encode_layer(k, p) for k, p in layers)
    Path(dst).write_bytes(body)
    print(f"{dst}: {len(body)} bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
