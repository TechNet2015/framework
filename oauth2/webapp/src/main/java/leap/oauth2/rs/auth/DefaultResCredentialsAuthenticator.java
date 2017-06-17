/*
 * Copyright 2015 the original author or authors.
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
package leap.oauth2.rs.auth;

import leap.core.BeanFactory;
import leap.core.annotation.Inject;
import leap.core.cache.Cache;
import leap.core.cache.CacheManager;
import leap.core.ioc.PostCreateBean;
import leap.core.security.ClientPrincipal;
import leap.core.security.UserPrincipal;
import leap.lang.Result;
import leap.lang.Strings;
import leap.lang.expirable.TimeExpirableMs;
import leap.lang.expirable.TimeExpirableSeconds;
import leap.lang.logging.Log;
import leap.lang.logging.LogFactory;
import leap.oauth2.webapp.OAuth2Config;
import leap.oauth2.rs.token.ResAccessToken;
import leap.oauth2.rs.token.ResAccessTokenDetails;
import leap.oauth2.rs.token.ResTokenManager;
import leap.oauth2.webapp.user.UserDetailsLookup;

/**
 * Default implementation of {@link ResCredentialsAuthenticator}.
 */
public class DefaultResCredentialsAuthenticator implements ResCredentialsAuthenticator, PostCreateBean {
    private static final Log log = LogFactory.get(DefaultResCredentialsAuthenticator.class);

    protected @Inject BeanFactory       factory;
    protected @Inject OAuth2Config      config;
    protected @Inject ResTokenManager   tokenManager;
    protected @Inject UserDetailsLookup userDetailsLookup;
    protected @Inject CacheManager      cacheManager;

    protected Cache<String, CachedAuthentication> authcCache;
    protected int                                 cacheSize = 2048;              //caches max {cacheSize} access tokens.
    protected int                                 cacheExpiresInMs = 120 * 1000; //2 minutes

    @Override
    public Result<ResAuthentication> authenticate(ResAccessToken at) {
        //Resolve from cache.
        CachedAuthentication cached = getCachedAuthentication(at);
        if(null != cached) {

            //Check expiration of token.
            if(cached.isTokenExpired()) {
                log.debug("Access token '{}' was expired", at.getToken());
                removeCachedAuthentication(at, cached);
                return Result.empty();
            }

            //Check expiration of the cached item.
            if(cached.isCacheExpired()) {
                log.debug("Cached authentication expired, remove it from cache only");
                removeCachedAuthentication(at, cached);
            }else{
                log.debug("Returns the cached authentication of access token : {}", at.getToken());
                return Result.of(cached.authentication);
            }
        }

        Result<ResAccessTokenDetails> result = tokenManager.loadAccessTokenDetails(at);
        if(!result.isPresent()) {
            log.debug("Access token '{}' not found", at.getToken());
            return Result.empty();
        }

        ResAccessTokenDetails details = result.get();
        if(details.isExpired()) {
            log.debug("Access token '{}' was expired", at.getToken());
            tokenManager.removeAccessToken(at);
            return Result.empty();
        }

        String clientId = details.getClientId();
        String userId   = details.getUserId();

        UserPrincipal   user   = null;
        ClientPrincipal client = null;

        if(!Strings.isEmpty(userId)) {
            user = userDetailsLookup.lookupUserDetails(at, userId);
            if(null == user) {
                //todo: exception?
                log.debug("User info not exists in remote authz server, user id -> {}, access token -> {}", userId, at.getToken());
                return Result.empty();
            }
        }

        if(!Strings.isEmpty(clientId)) {
            client = new ResClientPrincipal(clientId);
        }

        ResAuthentication authc = new SimpleResAuthentication(at, user, client);
        if(null != details.getScope()) {
            authc.setPermissions(Strings.split(details.getScope(), ","));
        }

        cacheAuthentication(at, details, authc);

        return Result.of(authc);
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public int getCacheExpiresInMs() {
        return cacheExpiresInMs;
    }

    public void setCacheExpiresInMs(int cacheExpiresInMs) {
        this.cacheExpiresInMs = cacheExpiresInMs;
    }

    @Override
    public void postCreate(BeanFactory factory) throws Throwable {
        authcCache = cacheManager.createSimpleLRUCache(cacheSize);
    }

    protected CachedAuthentication getCachedAuthentication(ResAccessToken at) {
        return authcCache.get(at.getToken());
    }

    protected void cacheAuthentication(ResAccessToken at, ResAccessTokenDetails tokenDetails, ResAuthentication authc) {
    	int cachedMs=cacheExpiresInMs;
    	if(tokenDetails instanceof TimeExpirableSeconds){
    		cachedMs=((TimeExpirableSeconds)tokenDetails).getExpiresInFormNow()*1000;
    	}
        authcCache.put(at.getToken(), new CachedAuthentication(tokenDetails, authc, cachedMs));
    }

    protected void removeCachedAuthentication(ResAccessToken at, CachedAuthentication cached) {
        authcCache.remove(at.getToken());
    }

    protected static final class CachedAuthentication {
        public final ResAccessTokenDetails tokenDetails;
        public final ResAuthentication     authentication;

        private final TimeExpirableMs expirable;

        public CachedAuthentication(ResAccessTokenDetails d, ResAuthentication a, int expiresInMs) {
            this.tokenDetails = d;
            this.authentication = a;
            this.expirable = new TimeExpirableMs(expiresInMs);
        }

        public boolean isTokenExpired() {
            return tokenDetails.isExpired();
        }

        public boolean isCacheExpired() {
            return expirable.isExpired();
        }
    }
}