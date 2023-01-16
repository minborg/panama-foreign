# sys/sysctl.h
# https://nxmnpg.lemoda.net/3/sysctlbyname
jextract --source \
  -t jdk.internal.include.sys.sysctl \
  --output ../../../../.. \
  -I /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/sysctl.h \
  /Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include/sys/sysctl.h \
  --include-function sysctlbyname \
  --include-typedef size_t

# --dump-includes includes.txt
