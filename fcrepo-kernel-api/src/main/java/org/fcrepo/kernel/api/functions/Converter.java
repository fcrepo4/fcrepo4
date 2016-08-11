/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.kernel.api.functions;

/**
 * @author acoburn
 * @author ajs6f
 * @since 6/20/16
 * @param <A> the type from which we are translating
 * @param <B> the type to which we are translating
 */
public interface Converter<A, B> extends ReversibleFunction<A, B>, DomainRestrictedFunction<A, B> {

    /**
     * @see org.fcrepo.kernel.api.functions.ReversibleFunction#reverse()
     * @return a Converter applying the reverse function
     */
    @Override
    public Converter<B, A> reverse();

    /**
     * @param <C> the range type of the subsequent function
     * @param after a converter to which this converter provides the domain
     * @return a composite function
     */
    public default <C> Converter<A, C> andThen(final Converter<B, C> after) {
        return new CompositeConverter<>(this, after);
    }

    /**
     *
     * @param <C> the domain type of the previous function
     * @param before a converter for which the range is this converter's domain
     * @return a composite function
     */
    public default <C> Converter<C, B> compose(final Converter<C, A> before) {
        return new CompositeConverter<>(before, this);
    }
}
