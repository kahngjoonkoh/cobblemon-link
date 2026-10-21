"""Check that two Bedrock packs hold the same content: same files, JSON compared as data (so a minified pack
matches its pretty-printed original), everything else byte for byte. Prints both packs' sizes.

  python3 tools/pack_equal.py before.zip after.zip   # exit 1 on any difference; `demo` runs a self-check
"""
import io, json, sys, zipfile

IGNORE = {"manifest.json"}  # GeyserModelEngine writes fresh pack UUIDs whenever it regenerates


def stats(z):
    files = [i for i in z.infolist() if not i.is_dir()]
    return (f"{sum(i.compress_size for i in files) / 1e6:.1f} MB zipped, {sum(i.file_size for i in files) / 1e6:.1f} MB "
            f"unpacked, {len(files)} files, {sum(i.filename.startswith('entity/') for i in files)} entities")


def diff(a, b):
    na = {i.filename for i in a.infolist() if not i.is_dir()} - IGNORE
    nb = {i.filename for i in b.infolist() if not i.is_dir()} - IGNORE
    out = [f"only in first: {n}" for n in sorted(na - nb)] + [f"only in second: {n}" for n in sorted(nb - na)]
    for n in sorted(na & nb):
        x, y = a.read(n), b.read(n)
        if n.endswith(".json"):
            x, y = json.loads(x), json.loads(y)
        if x != y:
            out.append(f"differs: {n}")
    return out


def demo():
    def pack(files):
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w") as z:
            for n, s in files.items():
                z.writestr(n, s)
        return zipfile.ZipFile(buf)
    pretty = pack({"a.json": '{\n  "x": [1, 2.5]\n}', "t.png": b"\x89PNG", "manifest.json": "{}"})
    assert diff(pretty, pack({"a.json": '{"x":[1,2.5]}', "t.png": b"\x89PNG", "manifest.json": '{"u":1}'})) == []
    assert diff(pretty, pack({"a.json": '{"x":[1,2.6]}', "t.png": b"\x89PNG"})) == ["differs: a.json"]
    assert diff(pretty, pack({"a.json": '{"x":[1,2.5]}'})) == ["only in first: t.png"]
    print("ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["demo"]:
        demo(); sys.exit()
    a, b = zipfile.ZipFile(sys.argv[1]), zipfile.ZipFile(sys.argv[2])
    print("first: ", stats(a)); print("second:", stats(b))
    problems = diff(a, b)
    print("\n".join(problems[:20]) or "same content")
    sys.exit(1 if problems else 0)
