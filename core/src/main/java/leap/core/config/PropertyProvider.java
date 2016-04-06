/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package leap.core.config;

/**
 * Used by {@link leap.core.AppConfig} for dynamic {@link Property} configuration.
 */
public interface PropertyProvider {

    /**
     * Returns the {@link Property} for the given type.
     */
    <T> Property<T> getProperty(String name,Class<T> type);

    /**
     * Returns the {@link StringProperty}.
     */
    default StringProperty getProperty(String name) {
        return new WrappedStringProperty(getProperty(name, String.class));
    }

    /**
     * Returns the {@link IntegerProperty}.
     *
     * <p/>
     * The returned {@link Property} object cannot be null.
     */
    default IntegerProperty getIntegerProperty(String name) {
        return new WrappedIntegerProperty(getProperty(name, Integer.class));
    }

    /**
     * Returns the {@link LongProperty}.
     *
     * <p/>
     * The returned {@link Property} object cannot be null.
     */
    default LongProperty getLongProperty(String name) {
        return new WrappedLongProperty(getProperty(name, Long.class));
    }

    /**
     * Returns the {@link BooleanProperty}.
     *
     * <p/>
     * The returned {@link Property} object cannot be null.
     */
    default BooleanProperty getBooleanProperty(String name) {
        return new WrappedBooleanProperty(getProperty(name, Boolean.class));
    }

    /**
     * Returns the {@link DoubleProperty}.
     *
     * <p/>
     * The returned {@link Property} object cannot be null.
     */
    default DoubleProperty getDoubleProperty(String name) {
        return new WrappedDoubleProperty(getProperty(name, Double.class));
    }

}