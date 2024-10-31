package io.github.suppierk.ddd.jooq;

import static org.junit.jupiter.api.Assertions.*;

import io.github.suppierk.test.EmptyDomainMessage;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class DslContextProviderTest {
  @Test
  void when_identity_provider_is_requested_with_null_argument_it_throws_an_exception() {
    assertThrows(IllegalArgumentException.class, () -> DslContextProvider.dslContextIdentity(null));
  }

  @Test
  void when_identity_provider_is_requested_with_correct_argument_it_returns_that_argument() {
    final var dslContext = DSL.using(SQLDialect.DEFAULT);

    final var dslContextProvider =
        assertDoesNotThrow(() -> DslContextProvider.dslContextIdentity(dslContext));

    final var providedDslContext =
        assertDoesNotThrow(() -> dslContextProvider.apply(EmptyDomainMessage.getInstance()));

    assertNotNull(providedDslContext);
    assertEquals(dslContext, providedDslContext);
  }
}
