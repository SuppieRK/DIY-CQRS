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
import org.jooq.Condition;

/**
 * Represents an immutable command which must update the underlying model as per CQRS paradigm.
 *
 * <p>It is highly recommended to use this interface with Java {@link Record}s.
 *
 * <p>In terms of 'read-write' {@link DomainCommand} is a 'write' representation, whereas {@link
 * DomainQuery} is its 'read' counterpart.
 *
 * <p>Commands must be task-oriented, not data-centric - e.g. 'Start Data Transfer' instead of 'Set
 * Data Transfer status to STARTED'.
 *
 * <p>Commands are asynchronous by nature, and it should be possible to place commands in a queue
 * rather than process them synchronously - this is the reason this class implements {@link
 * DomainMessage} interface which extends {@link Serializable} interface.
 *
 * <p>Most of the software systems rely on Create-Read-Update-Delete paradigm - out of those four
 * three can be represented as commands. To bridge the gap in understanding between CRUD and CQRS,
 * we leverage new Java {@code sealed} feature, enforcing users to use one of the specific
 * CRUD-related commands rather than defining a command completely on their own.
 *
 * <p>Some commands could be expressed with pure SQL more efficiently and request to supply {@link
 * Condition} upon implementation.
 *
 * @param <I> is the type of the command identifier
 * @param <T> is the type of the timestamp when this command was created
 */
// @formatter:off
public sealed interface DomainCommand<
  I extends Serializable,
  T extends Temporal & Serializable
> extends DomainMessage<I, T>
permits
  DomainCommand.Create, DomainCommand.BatchCreate,
  DomainCommand.Update, DomainCommand.BatchUpdate,
  DomainCommand.Delete, DomainCommand.BatchDelete
{
// @formatter:on

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to create a new
   * model in the system.
   */
  // @formatter:off
  non-sealed interface Create<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {}
  // @formatter:on

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to update an
   * existing model in the system.
   */
  // @formatter:off
  non-sealed interface Update<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {
  // @formatter:on

    /**
     * @return a {@link Condition} to select a record to work with
     */
    Condition condition();
  }

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to delete an
   * existing model in the system.
   */
  // @formatter:off
  non-sealed interface Delete<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {
  // @formatter:on

    /**
     * @return a {@link Condition} to select a record to work with
     */
    Condition condition();
  }

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to create
   * multiple new models in the system.
   */
  // @formatter:off
  non-sealed interface BatchCreate<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {}
  // @formatter:on

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to update
   * multiple existing models in the system.
   */
  // @formatter:off
  non-sealed interface BatchUpdate<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {
  // @formatter:on

    /**
     * @return a {@link Condition} to select a record to work with
     */
    Condition condition();
  }

  /**
   * Marker interface, denoting that the command is supposed to represent an intent to delete
   * multiple existing models in the system.
   */
  // @formatter:off
  non-sealed interface BatchDelete<
    I extends Serializable,
    T extends Temporal & Serializable
  > extends DomainCommand<I, T> {
  // @formatter:on

    /**
     * @return a {@link Condition} to select a record to work with
     */
    Condition condition();
  }
}
