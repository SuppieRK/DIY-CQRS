package io.github.suppierk.test;

import io.github.suppierk.ddd.authorization.DomainClient;
import java.io.Serial;

public final class AnotherDomainClient implements DomainClient {
  @Serial private static final long serialVersionUID = -2914859398443221507L;

  private AnotherDomainClient() {
    // No instance
  }

  public static AnotherDomainClient getInstance() {
    return Holder.INSTANCE;
  }

  @Override
  public String domainRole() {
    return "OTHER";
  }

  private static class Holder {
    private static final AnotherDomainClient INSTANCE = new AnotherDomainClient();
  }
}
