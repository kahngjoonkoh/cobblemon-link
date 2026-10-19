# ponytail: XTEST keystrokes into Xvfb :99. usage: xkey.py "/cmd" | xkey.py --key Escape | xkey.py --click
import sys, time
from Xlib import X, XK, display
from Xlib.ext import xtest
d = display.Display(':98')
def tap(sym, shift=False):
    kc = d.keysym_to_keycode(XK.string_to_keysym(sym))
    if shift: xtest.fake_input(d, X.KeyPress, d.keysym_to_keycode(XK.XK_Shift_L))
    xtest.fake_input(d, X.KeyPress, kc); d.sync(); time.sleep(0.03)
    xtest.fake_input(d, X.KeyRelease, kc)
    if shift: xtest.fake_input(d, X.KeyRelease, d.keysym_to_keycode(XK.XK_Shift_L))
    d.sync(); time.sleep(0.03)
NAMES = {'!': 'exclam', '^': 'asciicircum', '~': 'asciitilde', '[': 'bracketleft', ']': 'bracketright', '=': 'equal', ',': 'comma', '@': 'at', ' ': 'space', '/': 'slash', '_': 'underscore', '-': 'minus', ':': 'colon', '.': 'period'}
SHIFTED = set('_:^~@!') | set('ABCDEFGHIJKLMNOPQRSTUVWXYZ')
a = sys.argv[1:]
if a[0] == '--key':
    for k in a[1:]: tap(k)
elif a[0] == '--hold':  # --hold sym ms [gap_ms count]: press long enough for a game tick to see it
    kc = d.keysym_to_keycode(XK.string_to_keysym(a[1]))
    for i in range(int(a[4]) if len(a) > 4 else 1):
        if i: time.sleep(int(a[3]) / 1000)
        xtest.fake_input(d, X.KeyPress, kc); d.sync(); time.sleep(int(a[2]) / 1000)
        xtest.fake_input(d, X.KeyRelease, kc); d.sync()
elif a[0] == '--click':
    b = int(a[1]) if len(a) > 1 else 1
    xtest.fake_input(d, X.ButtonPress, b); d.sync(); time.sleep(0.05); xtest.fake_input(d, X.ButtonRelease, b); d.sync()
elif a[0] == '--move':
    xtest.fake_input(d, X.MotionNotify, x=int(a[1]), y=int(a[2])); d.sync()
elif a[0] == '--rel':  # relative pointer move by dx dy from where it is now
    for _ in range(4):  # in small steps, like a real mouse
        xtest.fake_input(d, X.MotionNotify, detail=1, x=int(a[1]) // 4, y=int(a[2]) // 4); d.sync(); time.sleep(0.02)
else:
    tap('t'); time.sleep(0.4)
    for c in a[0]: tap(NAMES.get(c, c), c in SHIFTED)
    time.sleep(0.2); tap('Return')
