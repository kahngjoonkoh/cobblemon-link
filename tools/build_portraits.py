"""Re-render every look's portraits (build_all.py does it while converting; this redoes just the pictures).

usage: build_portraits.py COBBLEMON_RESOURCES OUT_DIR   (OUT_DIR/java/<name>.bbmodel in, OUT_DIR/portraits/ out)
"""
import base64, json, os, sys
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(__file__))
import build_all
import portrait


def one(job, out):
    name, _, _, scale, _, framing = job
    p = f'{out}/java/{name}.bbmodel'
    if not os.path.exists(p):
        return
    bb = json.load(open(p))
    png = base64.b64decode(bb['textures'][0]['source'].split(',', 1)[1])
    for suffix, rev in (('', False), ('_r', True)):
        portrait.render(bb, png, *framing, scale, rev).save(f'{out}/portraits/{name}{suffix}.png')


if __name__ == '__main__':
    res, out = sys.argv[1:3]
    os.makedirs(f'{out}/portraits', exist_ok=True)
    jobs = build_all.plan(res)
    with Pool() as pool:
        pool.starmap(one, [(j, out) for j in jobs])
    print(len(jobs), 'looks')
