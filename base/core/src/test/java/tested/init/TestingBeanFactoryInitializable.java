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

package tested.init;

import leap.core.AppConfig;
import leap.core.BeanFactory;
import leap.core.BeanFactoryInitializable;
import leap.core.annotation.ConfigProperty;
import leap.core.ioc.BeanDefinitionConfigurator;
import leap.core.ioc.BeanDefinitions;

public class TestingBeanFactoryInitializable implements BeanFactoryInitializable {

    public static boolean called;

    @Override
    public void postInit(AppConfig config, BeanFactory factory, BeanDefinitions definitions) throws Throwable {
        called = true;

        BeanDefinitionConfigurator bd = definitions.getOrAdd(TestingBean.class);
        bd.setConfigurable(true);
        bd.setConfigurationPrefix("props");

    }

    public static final class TestingBean {

        public @ConfigProperty String prop1;

    }

}
