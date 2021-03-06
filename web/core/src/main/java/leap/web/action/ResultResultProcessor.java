/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package leap.web.action;

import leap.web.Result;

/**
 * The {@link ResultProcessor} to process the returned {@link Result} value.
 */
public final class ResultResultProcessor extends AbstractResultProcessor implements ResultProcessor {
	
	public static final ResultResultProcessor INSTANCE = new ResultResultProcessor();
	
	@Override
    public void processReturnValue(ActionContext context, Object returnValue, Result result) throws Throwable {
		if(returnValue == result){
			//do nothing.
		}else{
			result.setResult((Result)returnValue);
		}
    }
}