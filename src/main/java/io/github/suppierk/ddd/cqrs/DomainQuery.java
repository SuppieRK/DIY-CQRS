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

import java.io.Serializable;
import java.time.temporal.Temporal;

/**
 * Represents a query which must retrieve the underlying model as per CQRS paradigm.
 *
 * <p>In terms of 'read-write' {@link DomainQuery} is a 'read' representation, whereas {@link
 * DomainCommand} is its 'write' counterpart.
 *
 * <p>Queries must answer specific question using given data, while not necessarily retrieving the
 * whole model, effectively acting as a view of the underlying data - e.g. 'What is the status of
 * Data Transfer X' instead of 'Given Data Transfer X, fetch me its status'.
 *
 * <p>Because {@link DomainCommand} is asynchronous by nature, {@link DomainQuery} has an eventual
 * consistency guarantee.
 *
 * @param <I> is the type of the query identifier
 * @param <T> is the type of the timestamp when this query was created
 */
// @formatter:off
public sealed interface DomainQuery<
  I extends Serializable,
  T extends Temporal & Serializable
> extends DomainMessage<I, T>
permits
  DomainQuery.One, DomainQuery.Many
{
// @formatter:on

  /**
   * Marker interface, denoting that the query is supposed to represent an intent to read a single
   * model in the system.
   */
  // @formatter:off
  non-sealed interface One<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainQuery<I, T> {}
  // @formatter:on

  /**
   * Marker interface, denoting that the query is supposed to represent an intent to read multiple
   * models in the system.
   */
  // @formatter:off
  non-sealed interface Many<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainQuery<I, T> {}
  // @formatter:on
}
