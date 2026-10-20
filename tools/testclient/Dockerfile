# Test client for the Paper test server: Xvfb + the vanilla Minecraft client (portablemc, software GL) in a container,
# driven with xkey.py (XTEST keys/clicks) and snap.py (screenshots). Not part of the server.
#   docker build -t clpaper-client tools/testclient
#   docker run -d --name clpaper-client --network host -v ~/clpaper/mc:/mc clpaper-client
#   docker exec clpaper-client join.sh <host> <port> <name>
FROM ubuntu:26.04
# the same Mesa and Xvfb as the host it was first made on (an older Mesa finds no GLX visual for the client)
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-25-jre xvfb python3-pip python3-pil libgl1 libgl1-mesa-dri libegl1 mesa-utils \
      libglx-mesa0 libxrender1 libxtst6 libxi6 libxcursor1 libxrandr2 libxxf86vm1 libasound2t64 \
    && pip3 install --break-system-packages portablemc==4.4.1 python-xlib==0.33 && rm -rf /var/lib/apt/lists/*
COPY xkey.py snap.py join.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/join.sh
# SDL finds no GLX visual in this Xvfb; over EGL it works
ENV DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1
CMD ["sh", "-c", "mkdir -p /fb && exec Xvfb $DISPLAY -screen 0 1280x720x24 -fbdir /fb"]
