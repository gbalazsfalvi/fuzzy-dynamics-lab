package io.github.gbalazsfalvi.fuzzy.experiments;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.gbalazsfalvi.fuzzy.arithmetic.Rational;
import java.io.IOException;
import java.math.BigDecimal;

/** Shared exact string encoding for scientific evidence, with strict JSON syntax. */
final class EvidenceJson {
  static final Gson FORMAT =
      new GsonBuilder()
          .setStrictness(Strictness.STRICT)
          .registerTypeAdapter(BigDecimal.class, new DecimalStrings().nullSafe())
          .registerTypeAdapter(Rational.class, new RationalStrings().nullSafe())
          .setPrettyPrinting()
          .serializeNulls()
          .disableHtmlEscaping()
          .create();

  private EvidenceJson() {}

  private static String exactString(JsonReader in) throws IOException {
    if (in.peek() != JsonToken.STRING) {
      throw new JsonParseException(
          "Exact numerical evidence must be a JSON string: " + in.getPath());
    }
    return in.nextString();
  }

  private static final class DecimalStrings extends TypeAdapter<BigDecimal> {
    @Override
    public void write(JsonWriter out, BigDecimal value) throws IOException {
      out.value(value.toString());
    }

    @Override
    public BigDecimal read(JsonReader in) throws IOException {
      return new BigDecimal(exactString(in));
    }
  }

  private static final class RationalStrings extends TypeAdapter<Rational> {
    @Override
    public void write(JsonWriter out, Rational value) throws IOException {
      out.value(value.toString());
    }

    @Override
    public Rational read(JsonReader in) throws IOException {
      return Rational.parse(exactString(in));
    }
  }
}
