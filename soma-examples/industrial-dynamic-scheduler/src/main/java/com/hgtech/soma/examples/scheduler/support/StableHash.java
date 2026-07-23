package com.hgtech.soma.examples.scheduler.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 只用于可重放输入和结果证据的稳定 SHA-256 编码。 */
public final class StableHash {
  private final MessageDigest digest;

  public StableHash() {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by Java 8", impossible);
    }
  }

  public StableHash addString(String value) {
    if (value == null) {
      return addInt(-1);
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    addInt(bytes.length);
    digest.update(bytes);
    return this;
  }

  public StableHash addBoolean(boolean value) {
    digest.update((byte) (value ? 1 : 0));
    return this;
  }

  public StableHash addInt(int value) {
    digest.update((byte) (value >>> 24));
    digest.update((byte) (value >>> 16));
    digest.update((byte) (value >>> 8));
    digest.update((byte) value);
    return this;
  }

  public StableHash addLong(long value) {
    digest.update((byte) (value >>> 56));
    digest.update((byte) (value >>> 48));
    digest.update((byte) (value >>> 40));
    digest.update((byte) (value >>> 32));
    digest.update((byte) (value >>> 24));
    digest.update((byte) (value >>> 16));
    digest.update((byte) (value >>> 8));
    digest.update((byte) value);
    return this;
  }

  public String finishHex() {
    byte[] bytes = digest.digest();
    StringBuilder text = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      text.append(Character.forDigit((value >>> 4) & 0xf, 16));
      text.append(Character.forDigit(value & 0xf, 16));
    }
    return text.toString();
  }
}
