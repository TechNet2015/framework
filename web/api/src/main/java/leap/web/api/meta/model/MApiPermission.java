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
package leap.web.api.meta.model;

import leap.lang.Args;

import java.util.Map;

public class MApiPermission extends MApiObject {

    protected final String value;
    protected final String description;
    
    public MApiPermission(String value, String desc) {
        this(value, desc, null);
    }

    public MApiPermission(String value, String desc, Map<String, Object> attrs) {
        super(attrs);
        Args.notEmpty(value, "permission value");
        this.value = value;
        this.description = desc;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
