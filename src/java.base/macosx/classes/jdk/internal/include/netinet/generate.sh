jextract --source  \
  -t jdk.internal.include.netinet \
  --output ../../../.. \
  -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/ \
  /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/netinet/in.h \
  --include-struct sockaddr \
  --include-struct sockaddr_in \
  --include-struct sockaddr_in6 \
  --include-constant AF_INET \
  --include-constant AF_INET6 \
  --include-constant AF_UNSPEC
