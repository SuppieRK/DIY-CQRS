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

/**
 * Defines general contract rules used by the codebase.
 *
 * <p>Here is an example to help explain how different domain objects are related to each other -
 * let's assume that we are a driver in a car:
 *
 * <ul>
 *   <li>We, as a driver, are a {@link io.github.suppierk.ddd.authorization.DomainClient} - we can
 *       control and inspect car we are driving.
 *   <li>The road we are driving on is also a {@link
 *       io.github.suppierk.ddd.authorization.DomainClient} - it affects our driving from the
 *       outside.
 *   <li>Each car wheel can be represented as its own {@link org.jooq.UpdatableRecord}:
 *       <ul>
 *         <li>We, as a driver, can control wheel rotation speed or turning angle via {@link
 *             io.github.suppierk.ddd.cqrs.DomainCommand}s:
 *             <ul>
 *               <li>We as a {@link io.github.suppierk.ddd.authorization.DomainClient} might send a
 *                   {@link io.github.suppierk.ddd.cqrs.DomainCommand} to {@code Turn Left} which
 *                   will change wheel's {@link org.jooq.UpdatableRecord} property {@code
 *                   turningAngle}.
 *             </ul>
 *         <li>We, as a driver, can inspect wheel state via {@link
 *             io.github.suppierk.ddd.cqrs.DomainQuery} to either speedometer or steering wheel:
 *             <ul>
 *               <li>We as a {@link io.github.suppierk.ddd.authorization.DomainClient} might send a
 *                   {@link io.github.suppierk.ddd.cqrs.DomainQuery} to {@code Check Tire Pressure}
 *                   which will retrieve wheel's {@link org.jooq.UpdatableRecord} property {@code
 *                   tirePressure} and present it back to us.
 *             </ul>
 *         <li>We, as a driver, can inspect the overall state via {@link
 *             io.github.suppierk.ddd.cqrs.DomainView} to any car components:
 *             <ul>
 *               <li>We as a {@link io.github.suppierk.ddd.authorization.DomainClient} might send a
 *                   {@link io.github.suppierk.ddd.cqrs.DomainView} to {@code Check Tire Pressure}
 *                   (like some of us do when we get out of the car and push the wheel with our leg
 *                   to see if it will behave funny) which will retrieve wheel's {@link
 *                   org.jooq.UpdatableRecord} property {@code tirePressure} along with some other
 *                   additional properties and present it back to us in a form of a user-defined
 *                   object.
 *             </ul>
 *         <li>Wheels can communicate with suspension via {@link
 *             io.github.suppierk.ddd.async.DomainNotification}s (and vice versa):
 *             <ul>
 *               <li>Wheel {@link org.jooq.UpdatableRecord} upon reacting to a {@link
 *                   io.github.suppierk.ddd.cqrs.DomainCommand} to adjust its property {@code
 *                   turningAngle} might send an {@link
 *                   io.github.suppierk.ddd.async.DomainNotification}, which when received by
 *                   suspension can trigger a change in suspension's {@link
 *                   org.jooq.UpdatableRecord} property {@code rebound}.
 *             </ul>
 *       </ul>
 *   <li>Wheel, combined with actions we can do, form a {@link
 *       io.github.suppierk.ddd.cqrs.BoundedContext} describing possible interactions.
 * </ul>
 */
package io.github.suppierk.ddd.cqrs;
