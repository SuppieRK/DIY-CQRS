package io.github.suppierk.ddd.cqrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.suppierk.ddd.authorization.AnonymousDomainClient;
import io.github.suppierk.ddd.authorization.DomainClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainMessageTest {
  @Test
  void when_auditable_created_without_client_auditable_gets_anonymous_client() {
    final var noExplicitClientAuditable =
        new NoExplicitClientDomainMessage(UUID.randomUUID(), Instant.now());
    final var explicitClientAuditable =
        new ExplicitClientDomainMessage(
            UUID.randomUUID(), Instant.now(), AnonymousDomainClient.getInstance());

    assertNotNull(noExplicitClientAuditable.domainClient());
    assertEquals("ANONYMOUS", noExplicitClientAuditable.domainClient().domainRole());

    assertNotNull(explicitClientAuditable.domainClient());
    assertEquals("ANONYMOUS", explicitClientAuditable.domainClient().domainRole());
  }

  /**
   * A sample {@link DomainMessage}, showcasing the interesting Java abilities to:
   *
   * <ul>
   *   <li>Have a {@link Record} implement an {@code interface}.
   *   <li>Have an ability to not implement {@code interface} methods directly when {@link Record}
   *       has fields with names matching {@code interface} methods.
   *   <li>Have the ability to skip defining some fields in {@link Record} when interface has {@code
   *       default} methods.
   * </ul>
   *
   * @param messageId to fulfill {@link DomainMessage#messageId()} contract
   * @param createdAt to fulfill {@link DomainMessage#createdAt()} contract
   */
  record NoExplicitClientDomainMessage(UUID messageId, Instant createdAt)
      implements DomainMessage<UUID, Instant> {}

  /**
   * A sample {@link DomainMessage} with explicitly defined client.
   *
   * @param messageId to fulfill {@link DomainMessage#messageId()} contract
   * @param createdAt to fulfill {@link DomainMessage#createdAt()} contract
   * @param domainClient to explicitly fulfill {@link DomainMessage#domainClient()} contract
   */
  record ExplicitClientDomainMessage(UUID messageId, Instant createdAt, DomainClient domainClient)
      implements DomainMessage<UUID, Instant> {}
}
