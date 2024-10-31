package io.github.suppierk.ddd.async;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DomainNotificationProducerTest {
  @Test
  void when_empty_publisher_is_requested_it_must_not_be_null_and_it_must_be_functional() {
    final var publisher = DomainNotificationProducer.empty();

    assertNotNull(publisher);
    assertDoesNotThrow(() -> publisher.store(null, null));
  }
}
