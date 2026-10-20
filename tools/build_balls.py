"""Every Poké Ball's own 3D model for its throw and capture (PokeBalls.java): Cobblemon draws them all on two
geometries (poke_ball, ancient_poke_ball) with each ball's texture, so this converts that pair once per ball, the
same way build_all.py converts the Poké Ball.

usage: build_balls.py COBBLEMON_RESOURCES OUT_DIR   -> OUT_DIR/balls/java/<ball>.bbmodel, OUT_DIR/balls/bedrock/<ball>/
"""
import glob, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import build_all


def jobs(res):
    A, B = f'{res}/assets/cobblemon', f'{res}/assets/cobblemon/bedrock/poke_balls'
    out = []
    for tex in sorted(glob.glob(f'{A}/textures/poke_balls/*.png')):
        ball = os.path.basename(tex)[:-4]
        if ball == 'poke_ball':
            continue  # build_all.py's
        base = 'ancient_poke_ball' if ball.startswith('ancient') else 'poke_ball'
        anim = f'{B}/animations/{base}.animation.json'
        if not os.path.exists(anim):
            anim = f'{B}/animations/poke_ball.animation.json'
        anims = {dst: (anim, dst) for dst in build_all.BALL} | {'idle': (anim, 'shut_idle')}
        out.append((ball, f'{B}/models/{base}.geo.json', [tex], 1.0, anims, None))
    return out


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    res, out = sys.argv[1:]
    os.makedirs(f'{out}/balls', exist_ok=True)
    done = [build_all.build(j, f'{out}/balls') for j in jobs(res)]
    print(sum('\tok' in d for d in done), 'of', len(done), 'balls ->', f'{out}/balls')
