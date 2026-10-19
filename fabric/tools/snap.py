# ponytail: XWD (Xvfb -fbdir) -> PNG, assumes 24-depth/32bpp little-endian, which is what Xvfb writes here
import struct, sys
from PIL import Image
d = open(sys.argv[1], 'rb').read()
h = struct.unpack('>25I', d[:100])
hs, w, ht, bpl, ncol = h[0], h[4], h[5], h[12], h[19]
off = hs + ncol * 12
Image.frombuffer('RGB', (w, ht), d[off:off + bpl * ht], 'raw', 'BGRX', bpl, 1).save(sys.argv[2])
