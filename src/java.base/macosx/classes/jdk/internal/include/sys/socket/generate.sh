# sys/socket.h
jextract --source \
  -t jdk.internal.include.sys.socket \
  --output ../../../../.. \
  -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h \
  /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/socket.h \
  --include-function sendto \
  --include-function recvfrom \
  --include-function disconnectx \
  --include-function connect \
  --include-constant SAE_ASSOCID_ANY \
  --include-constant SAE_CONNID_ANY \
  --include-typedef socklen_t
