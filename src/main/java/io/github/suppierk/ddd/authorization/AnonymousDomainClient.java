/*
 * Copyright 2024 Roman Khlebnov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.suppierk.ddd.authorization;

import java.io.Serial;

/** Default stub option to use as a {@link DomainClient}. */
@SuppressWarnings({"unchecked", "squid:S6548"})
public final class AnonymousDomainClient implements DomainClient {
  @Serial private static final long serialVersionUID = -2611523096852178379L;

  private static final String ROLE = "ANONYMOUS";

  private AnonymousDomainClient() {
    // Cannot be instantiated
  }

  /**
   * @return a default instance of the client
   */
  public static <T extends DomainClient> T getInstance() {
    return (T) Holder.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public String domainRole() {
    return ROLE;
  }

  /**
   * @see <a
   *     href="https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom">Initialization-on-demand
   *     holder idiom</a>
   */
  private static class Holder {
    private static final AnonymousDomainClient INSTANCE = new AnonymousDomainClient();
  }
}
