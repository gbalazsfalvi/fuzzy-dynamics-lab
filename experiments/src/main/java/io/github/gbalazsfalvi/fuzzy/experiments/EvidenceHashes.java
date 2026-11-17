package io.github.gbalazsfalvi.fuzzy.experiments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical typed JSON digests bind complete configurations without claiming authentication. */
final class EvidenceHashes {
  private EvidenceHashes() {}

  static String of(Object value) {
    return text(EvidenceJson.FORMAT.toJson(value));
  }

  static String text(String value) {
    return bytes(value.getBytes(StandardCharsets.UTF_8));
  }

  static String bytes(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("The Java runtime must provide SHA-256.", exception);
    }
  }
}
