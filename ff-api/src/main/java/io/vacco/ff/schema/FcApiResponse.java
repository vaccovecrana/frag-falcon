package io.vacco.ff.schema;

public class FcApiResponse {

  public int statusCode;
  public String body;

  public static FcApiResponse of(int statusCode, String body) {
    var r = new FcApiResponse();
    r.statusCode = statusCode;
    r.body = body;
    return r;
  }

  @Override public String toString() {
    return String.format("%d%s",
      statusCode,
      body != null && body.isEmpty() ? "" : " - " + body
    );
  }

}
