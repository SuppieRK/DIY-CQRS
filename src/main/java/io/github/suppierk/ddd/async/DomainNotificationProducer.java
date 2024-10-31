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

package io.github.suppierk.ddd.async;

import org.jooq.DSLContext;

/**
 * Abstract contract for an entity which is able to publish {@link DomainNotification} for
 * consumption.
 *
 * <p>For the functionality, it is crucial that the functionality which will be implemented here
 * will store the notification and then deliver the notification based on a schedule, rather than
 * send the notification immediately., implying that when one of the {@code Handler}s performs an
 * operation within transaction - notification is stored as a part of that transaction, enabling
 * better resilience.
 *
 * @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">Transactional
 *     outbox</a>
 */
public interface DomainNotificationProducer {
  /**
   * @return an instance of publisher which does not perform any operations
   */
  static DomainNotificationProducer empty() {
    return NoOp.INSTANCE;
  }

  /**
   * Saves {@link DomainNotification} to its own table for delivery.
   *
   * @param readWriteDsl is a transactional context with writing capability at the time when the
   *     operation takes place
   * @param notification to publish
   * @param <E> is a generic {@link DomainNotification} type
   */
  <E extends DomainNotification<?, ?>> void store(
      final DSLContext readWriteDsl, final E notification);

  /** Default implementation of the fake publisher */
  final class NoOp implements DomainNotificationProducer {
    private static final DomainNotificationProducer INSTANCE = new NoOp();

    private NoOp() {
      // Cannot be instantiated from the outside
    }

    @Override
    public <E extends DomainNotification<?, ?>> void store(
        final DSLContext readWriteDsl, final E notification) {
      // Do nothing
    }
  }
}
