package jdk.internal.foreign.mapper;

import java.lang.constant.ClassDesc;

record ScalarInfo(String memberName,
                  ClassDesc valueLayoutDesc) {
}
