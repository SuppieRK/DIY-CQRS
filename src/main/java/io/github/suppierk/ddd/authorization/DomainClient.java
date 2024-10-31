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

import java.io.Serializable;

/**
 * Represents a program element interacting with the system.
 *
 * <p>In most systems an entity is passed around as a {@link String}, however in this case {@link
 * DomainClient} simply defines common checks we want to perform without enforcing any particular
 * storage options, providing users with the ability to decide these details based on their
 * environment and business requirements.
 */
public interface DomainClient extends Serializable {
  /**
   * @return assumed client's role within the domain used primarily for error reporting
   */
  String domainRole();
}
