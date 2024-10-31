package io.github.suppierk.ddd.authorization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UnauthorizedExceptionTest {
  @Test
  void all_exception_constructors_must_be_present() {
    assertDoesNotThrow(() -> new UnauthorizedException());
    assertDoesNotThrow(() -> new UnauthorizedException("message"));
    assertDoesNotThrow(() -> new UnauthorizedException("message", new IllegalStateException()));
    assertDoesNotThrow(() -> new UnauthorizedException(new IllegalStateException()));
  }

  @Test
  void must_have_forbidden_http_status_code() {
    assertEquals(403, new UnauthorizedException().getStatusCode());
  }
}
