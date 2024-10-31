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

import io.github.suppierk.ddd.cqrs.DomainMessage;
import java.io.Serializable;
import java.time.temporal.Temporal;

/**
 * Represents a notification about a database change or access within the system.
 *
 * <p>Notifications can be used by other systems to be aware about changes taking place to be able
 * to asynchronously react and adapt.
 *
 * <p>Notifications are asynchronous by nature, and it should be possible to place them in a message
 * queue - this is the reason this class implements {@link Serializable} interface.
 *
 * @param <I> is the type of this notification identifier
 * @param <T> is the type of the timestamp when this notification was created
 */
// @formatter:off
public interface DomainNotification<
  I extends Serializable,
  T extends Temporal & Serializable
> extends DomainMessage<I, T> {}
// @formatter:on
