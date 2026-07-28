package com.example.soma.defaults;

final class DefaultLiterals {
    static final String X16 = "xxxxxxxxxxxxxxxx";
    static final String X64 = X16 + X16 + X16 + X16;
    static final String X256 = X64 + X64 + X64 + X64;
    static final String X1024 = X256 + X256 + X256 + X256;
    static final String X4096 = X1024 + X1024 + X1024 + X1024;
    static final String X4097 = X4096 + "x";

    private DefaultLiterals() {
    }
}
