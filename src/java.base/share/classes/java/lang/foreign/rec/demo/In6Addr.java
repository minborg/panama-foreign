package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.BitAlignment;
import java.lang.foreign.rec.Length;
import java.lang.foreign.rec.Union;

/*
 struct in6_addr {
   31     union {
   32         __u8        u6_addr8[16];
   33         __be16      u6_addr16[8];
   34         __be32      u6_addr32[4];
   35     } in6_u;
   39 };
 */

/**
 * IN6Addr
 * @param u8 u8
 * @param u6Addr16 u
 * @param u6Addr32 u
 */
@Union
public record In6Addr(@Length(16) byte[] u8,
                      @Length(8) @BitAlignment(8) byte[] u6Addr16,
                      @Length(4) @BitAlignment(8) byte[] u6Addr32) {
}


/*
@Union
public record In6Addr2(@Length(16) U6Addr8[] u8,
                      @Length(8) U6Addr8[] be16,
                      @Length(4) U6Addr16[] be32) {
}

public record U6Addr8(JavaByte javaByte){}
public record U6Addr16(@BitAlignment(8) JavaShort javaShort){}
public record U6AAddr32(@BitAlignment(8) JavaInt javaInt){}
*/

