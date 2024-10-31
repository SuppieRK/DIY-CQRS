package io.github.suppierk.ddd.authorization;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class AnonymousDomainClientTest {
  @Test
  void when_client_is_used_essential_properties_are_not_null() {
    assertNotNull(AnonymousDomainClient.getInstance());
    assertNotNull(AnonymousDomainClient.getInstance().domainRole());
  }
}
