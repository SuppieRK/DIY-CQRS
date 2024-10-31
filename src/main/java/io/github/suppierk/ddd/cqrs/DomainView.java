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
 * Represents a view which must retrieve the underlying models in a specific way, presumably using
 * {@code JOIN}s with other tables.
 *
 * <p>Views must answer specific question using given data, while not retrieving the whole model -
 * e.g. 'What is the status of Data Transfer X' instead of 'Given Data Transfer X, fetch me its
 * status'.
 *
 * <p>Because {@link DomainCommand} is asynchronous by nature, {@link DomainView} has at most an
 * eventual consistency guarantee.
 *
 * @param <I> is the type of the view identifier
 * @param <T> is the type of the timestamp when this view was created
 */
// @formatter:off
public sealed interface DomainView<
  I extends Serializable,
  T extends Temporal & Serializable
> extends DomainMessage<I, T>
permits
  DomainView.One, DomainView.Many
{
// @formatter:on

  /**
   * Marker interface, denoting that the view is supposed to represent an intent to read a single
   * object representing some state of the system.
   */
  // @formatter:off
  non-sealed interface One<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainView<I, T> {}
  // @formatter:on

  /**
   * Marker interface, denoting that the view is supposed to represent an intent to read multiple
   * objects representing some state of the system.
   */
  // @formatter:off
  non-sealed interface Many<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainView<I, T> {}
  // @formatter:on
}
