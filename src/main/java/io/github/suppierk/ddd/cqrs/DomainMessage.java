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

package io.github.suppierk.ddd.cqrs;

import io.github.suppierk.ddd.authorization.AnonymousDomainClient;
import io.github.suppierk.ddd.authorization.DomainClient;
import java.io.Serializable;
import java.time.temporal.Temporal;

/**
 * Describes general properties of system interactions which can be tracked or used for system
 * audit.
 *
 * @param <I> is the type of the interaction identifier
 * @param <T> is the type of the timestamp when this interaction was created
 */
// @formatter:off
public interface DomainMessage<
  I extends Serializable,
  T extends Temporal & Serializable
> extends Serializable {
// @formatter:on

  /**
   * Defined as {@code messageId()} because:
   *
   * <ul>
   *   <li>{@code getMessageId()} is not friendly towards Java {@link Record}s.
   *   <li>{@code id()} is quite frequently taken to describe the ID of the entity we are
   *       interacting with.
   * </ul>
   *
   * @return an identifier for the current interaction
   */
  I messageId();

  /**
   * @return the time when this interaction was created / constructed in the system.
   */
  T createdAt();

  /**
   * @return the client who interacts with the system
   */
  default DomainClient domainClient() {
    return AnonymousDomainClient.getInstance();
  }
}
