/*
 *
 *  * Copyright 2016 the original author or authors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package leap.core.meta;

import leap.lang.meta.MComplexType;
import leap.lang.meta.MTypeContext;
import leap.lang.meta.MTypeFactory;

import java.util.Map;
import java.util.function.Consumer;

public interface MTypeContainer extends MTypeFactory {

    /**
     * Runs the function with given {@link MTypeContext}.
     */
    void runWithContext(Consumer<MTypeContext> func);

    /**
     * Returns all complex types in this container.
     */
    Map<Class<?>, MComplexType> getComplexTypes();

}