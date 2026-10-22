"""Evaluates the UI pack's HUD bindings on a title string, as Bedrock does (string '-' removes every occurrence,
'%.Ns' * x keeps the first N characters), so a payload can be checked field by field.
usage: hudeval.py PACK TITLE"""
import json, re, sys, zipfile

class Str(str):
    def __sub__(self, o): return Str(self.replace(o, ''))
    def __rsub__(self, o): return Str(str(o).replace(self, ''))
    def __add__(self, o): return Str(str(self) + str(o))
    def __radd__(self, o): return Str(str(o) + str(self))
class Cut:
    def __init__(self, n): self.n = n
    def __mul__(self, x): return Str(str(x)[:self.n])

def ev(expr, v):
    e = re.sub(r"'%\.(\d+)s'", r"Cut(\1)", expr)
    e = re.sub(r"'((?:[^'])*)'", lambda m: f"Str({m.group(0)})", e)
    e = re.sub(r"(?<![=!<>])=(?!=)", "==", e)
    e = re.sub(r"#[a-z_0-9]+", lambda m: f"v[{m.group(0)!r}]", e)
    return eval(e, {"Cut": Cut, "Str": Str, "v": v})

def control(c, title):
    v = {"#hud_title_text_string": Str(title)}
    for b in c.get("bindings", []):
        if b.get("binding_type") == "view":
            v[b["target_property_name"]] = ev(b["source_property_name"], v)
    return v

if __name__ == '__main__':
    hud = json.loads(zipfile.ZipFile(sys.argv[1]).read('ui/hud_screen.json'))
    title = sys.argv[2]
    tiles = hud['cl_battle_tiles']['controls'][0]['desktop']['controls']
    for t in tiles:
        (name, panel), = t.items()
        if not control(panel, title).get('#visible'): continue
        print('tile', name)
        for c in panel['controls']:
            (cn, ctl), = c.items()
            v = control(ctl, title)
            out = {k: v[k] for k in ('#texture', '#text', '#visible') if k in v}
            if 'texture' in ctl: out.setdefault('#texture', ctl['texture'])
            if 'text' in ctl and ctl['text'] != '#text': out['#text'] = ctl['text']
            print(' ', cn, out)
