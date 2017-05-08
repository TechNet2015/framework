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

package app.controllers.testing;

import leap.web.annotation.http.GET;
import leap.web.api.mvc.ApiResponse;
import leap.web.api.mvc.ModelController;
import leap.web.api.mvc.params.QueryOptions;
import leap.core.security.annotation.AllowAnonymous;
import app.models.testing.User;

import java.util.List;

@AllowAnonymous
public class UserController extends ModelController<User> implements UserControllerDesc {

    @GET
    public ApiResponse<List<User>> getAllUsers(QueryOptions options) {
        return queryList(options);
    }

    @GET("/safe")
    public ApiResponse<List<User>> getAllUsersWithoutPassword(QueryOptions options) {
        return queryListWithExecutorCallback(options, (executor) -> {
           executor.selectExclude("password");
        });
    }

    @GET("/{id}")
    public ApiResponse<User> getUser(String id,QueryOptions options) {
        return get(id,options);
    }

}