# ponytail: XTEST keystrokes into Xvfb :99. usage: xkey.py "/cmd" | xkey.py --key Escape | xkey.py --click
import sys, time
from Xlib import X, XK, display
from Xlib.ext import xtest
d = display.Display(':99')
def tap(sym, shift=False):
    kc = d.keysym_to_keycode(XK.string_to_keysym(sym))
    if shift: xtest.fake_input(d, X.KeyPress, d.keysym_to_keycode(XK.XK_Shift_L))
    xtest.fake_input(d, X.KeyPress, kc); d.sync(); time.sleep(0.03)
    xtest.fake_input(d, X.KeyRelease, kc)
    if shift: xtest.fake_input(d, X.KeyRelease, d.keysym_to_keycode(XK.XK_Shift_L))
    d.sync(); time.sleep(0.03)
NAMES = {' ': 'space', '/': 'slash', '_': 'underscore', '-': 'minus', ':': 'colon', '.': 'period'}
SHIFTED = set('_:') | set('ABCDEFGHIJKLMNOPQRSTUVWXYZ')
a = sys.argv[1:]
if a[0] == '--key':
    for k in a[1:]: tap(k)
elif a[0] == '--click':
    b = int(a[1]) if len(a) > 1 else 1
    xtest.fake_input(d, X.ButtonPress, b); d.sync(); time.sleep(0.05); xtest.fake_input(d, X.ButtonRelease, b); d.sync()
elif a[0] == '--move':
    xtest.fake_input(d, X.MotionNotify, x=int(a[1]), y=int(a[2])); d.sync()
else:
    tap('t'); time.sleep(0.4)
    for c in a[0]: tap(NAMES.get(c, c), c in SHIFTED)
    time.sleep(0.2); tap('Return')
