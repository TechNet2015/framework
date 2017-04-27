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
package leap.core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import leap.core.ds.DataSourceConfig;
import leap.core.ds.DataSourceManager;
import leap.core.sys.SysPermission;
import leap.core.sys.SysPermissionDefinition;
import leap.lang.Args;
import leap.lang.Charsets;
import leap.lang.Classes;
import leap.lang.Collections2;
import leap.lang.Factory;
import leap.lang.Locales;
import leap.lang.Out;
import leap.lang.Strings;
import leap.lang.accessor.MapPropertyAccessor;
import leap.lang.accessor.PropertyGetter;
import leap.lang.convert.Converts;
import leap.lang.io.IO;
import leap.lang.logging.Log;
import leap.lang.logging.LogFactory;
import leap.lang.resource.Resource;
import leap.lang.resource.ResourceSet;
import leap.lang.resource.Resources;
import leap.lang.text.DefaultPlaceholderResolver;
import leap.lang.xml.XML;
import leap.lang.xml.XmlReader;

class DefaultAppConfigLoader {
	
	private static final Log log = LogFactory.get(DefaultAppConfigLoader.class);
	
	public static final String DEFAULT_NAMESPACE_URI = "http://www.leapframework.org/schema/config";
	
	private static final String CONFIG_ELEMENT             = "config";
	private static final String BASE_PACKAGE_ELEMENT       = "base-package";
	private static final String ADDITIONAL_PACKAGES_ELEMENT= "additional-packages";
	private static final String DEBUG_ELEMENT			   = "debug";
	private static final String DEFAULT_LOCALE             = "default-locale";
	private static final String DEFAULT_ENCODING           = "default-charset";
	private static final String DATASOURCE_ELEMENT		   = "datasource";
	private static final String IMPORT_ELEMENT             = "import";
	private static final String PROPERTIES_ELEMENT         = "properties";
	private static final String PROPERTY_ELEMENT           = "property";
	private static final String PERMISSIONS_ELEMENT        = "permissions";
	private static final String GRANT_ELEMENT              = "grant";
	private static final String DENY_ELEMENT               = "deny";
	private static final String RESOURCES_ELEMENT          = "resources";
	private static final String RESOURCE_ATTRIBUTE         = "resource";
	private static final String IF_PROFILE_ATTRIBUTE       = "if-profile";
	private static final String OVERRIDE_ATTRIBUTE         = "override";
	private static final String PREFIX_ATTRIBUTE		   = "prefix";
	private static final String TYPE_ATTRIBUTE             = "type";
	private static final String DEFAULT_ATTRIBUTE          = "default";
	private static final String CLASS_ATTRIBUTE            = "class";
	private static final String ACTIONS_ATTRIBUTE          = "actions";
	private static final String CHECK_EXISTENCE_ATTRIBUTE  = "check-existence";
	private static final String DEFAULT_OVERRIDE_ATTRIBUTE = "default-override";
	private static final String NAME_ATTRIBUTE             = "name";
	private static final String VALUE_ATTRIBUTE            = "value";
	private static final String LOCATION_ATTRIBUTE         = "location";
	
	private static final List<AppConfigProcessor> processors = Factory.newInstances(AppConfigProcessor.class);

	protected String  profileName;
	protected String  basePackage;
	protected Boolean debug;
	protected Locale  defaultLocale;
	protected Charset defaultCharset;
	protected Object  externalContext;

	protected final Set<String> additionalPackages = new LinkedHashSet<>();
	protected final Map<String, String> externalProperties;
	protected final Map<String, String>           properties  = new LinkedHashMap<>();
	protected final Map<Class<?>, Object>         extensions  = new HashMap<>();
	protected final Set<Resource>                 resources   = new HashSet<>();
	protected final List<SysPermissionDefinition> permissions = new ArrayList<>();
	protected final Map<String, DataSourceConfig> dataSourceConfigs;

	protected final Map<Class<?>, Map<String, SysPermissionDefinition>> typedPermissions = new HashMap<>();

	protected final DefaultPlaceholderResolver placeholderResolver;
	protected final AppPropertyProcessor       propertyProcessor;
	
	protected DefaultAppConfigLoader(){
		this.externalContext     = null;
		this.externalProperties  = null;
		this.placeholderResolver = null;
		this.propertyProcessor   = null;
		this.dataSourceConfigs   = new HashMap<>();
	}

	/*
	protected DefaultAppConfigLoader(Map<String, DataSourceConfig> dataSourceConfigs){
		this.externalContext     = null;
		this.placeholderResolver = null;
		this.dataSourceConfigs   = dataSourceConfigs;
	}

	protected DefaultAppConfigLoader(Object externalContext, Map<String, DataSourceConfig> dataSourceConfigs){
		this.externalContext     = externalContext;
		this.placeholderResolver = null;
		this.dataSourceConfigs   = dataSourceConfigs;
	}
	*/
	protected DefaultAppConfigLoader(Object externalContext,
									 Map<String, String> externalProperties,
									 Map<String, DataSourceConfig> dataSourceConfigs,
									 AppPropertyProcessor propertyProcessor){
		this.externalContext     = externalContext;
		this.externalProperties  = externalProperties;
		this.placeholderResolver = new DefaultPlaceholderResolver(new PropertyGetter() {
			@Override
			public String getProperty(String name) {
				if(properties.containsKey(name)) {
					return properties.get(name);
				}
				return null != externalProperties ? externalProperties.get(name) : null;
			}
		});
		this.placeholderResolver.setEmptyUnresolvablePlaceholders(false);
		this.placeholderResolver.setIgnoreUnresolvablePlaceholders(true);
		this.dataSourceConfigs = dataSourceConfigs;
		this.propertyProcessor = propertyProcessor;
	}
	
	protected DefaultAppConfigLoader load(Resource... resources){
		loadConfigs(new LoadContext(null, false), resources);
		return this;
	}
	
	protected DefaultAppConfigLoader load(String profile, Resource... resources){
		loadConfigs(new LoadContext(profile,false), resources);
		return this;
	}
	
	protected Map<String, String> getProperties() {
		return properties;
	}
	
	protected Set<Resource> getResources(){
		return resources;
	}
	
	protected List<SysPermissionDefinition> getPermissions(){
		return permissions;
	}
	
	protected void loadBaseProperties(Resource resource) {
		XmlReader reader = null;
		try{
			String resourceUrl = resource.getURL().toString();
			
			log.debug("Load base properties from config : {}",resourceUrl);
			
			reader = XML.createReader(resource);
			reader.setPlaceholderResolver(placeholderResolver);
			
			boolean foundValidRootElement = false;
			
			while(reader.next()){
				if(reader.isStartElement(CONFIG_ELEMENT)){
					foundValidRootElement = true;
					readBaseProperties(resource,reader);
					break;
				}
			}
			
			if(!foundValidRootElement){
				throw new AppConfigException("No valid root element found in file : " + resource.getClasspath());
			}
		}catch(IOException e) {
			throw new AppConfigException("I/O Exception",e);
		}finally{
			IO.close(reader);
		}
	}

	private void loadConfigs(LoadContext context,Resource... resources){
		for(Resource resource : resources){
			XmlReader reader = null;

			try{
				String resourceUrl = resource.getURL().toString();
				
				if(log.isDebugEnabled()){
	                if(AppContextInitializer.isFrameworkResource(resourceUrl)) {
	                    log.trace("Load config from resource : {}",resourceUrl);
	                }else{
	                    log.debug("Load config from resource : {}",resourceUrl);    
	                }
				}
				
				if(context.resources.contains(resourceUrl)){
					throw new AppConfigException("cycle importing detected, please check your config : " + resourceUrl);
				}

				context.resources.add(resourceUrl);
				
				reader = XML.createReader(resource);
				reader.setPlaceholderResolver(placeholderResolver);
				
				boolean foundValidRootElement = false;
				
				while(reader.next()){
					if(reader.isStartElement(CONFIG_ELEMENT)){
						foundValidRootElement = true;
						readConfig(context,resource,reader);
						break;
					}
				}
				
				if(!foundValidRootElement){
					throw new AppConfigException("No valid root element found in file : " + resource.getClasspath());
				}
			}catch(IOException e) {
				throw new AppConfigException("I/O Exception",e);
			}finally{
				IO.close(reader);
			}
		}
	}	
	
	private void readBaseProperties(Resource resource, XmlReader reader) {
		while(reader.nextWhileNotEnd(CONFIG_ELEMENT)){
			if(reader.isStartElement(BASE_PACKAGE_ELEMENT)){
				basePackage = reader.resolveElementTextAndEnd();
				continue;
			}
			
			if(reader.isStartElement(DEBUG_ELEMENT)){
				String debugValue = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(debugValue)){
					this.debug = Converts.toBoolean(debugValue);
				}
				continue;
			}
			
			if(reader.isStartElement(DEFAULT_LOCALE)){
				if(null != defaultLocale){
					throw new AppConfigException("default-locale aleady defined as '" + defaultLocale.toString() + "', duplicated config in xml : " + reader.getSource());
				}
				String defaultLocaleString = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(defaultLocaleString)){
					defaultLocale = Locales.forName(defaultLocaleString);
				}
				continue;
			}
			
			if(reader.isStartElement(DEFAULT_ENCODING)){
				if(null != defaultCharset){
					throw new AppConfigException("default-charset aleady defined as '" + defaultCharset.name() + "', duplicated config in xml : " + reader.getSource());
				}
				String defaultEncodingName = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(defaultEncodingName)){
					defaultCharset = Charsets.forName(defaultEncodingName);
				}
				continue;
			}
		}
	}
	
	private void readConfig(LoadContext context,Resource resource, XmlReader reader) {
		if(!matchProfile(context.getProfile(), reader)){
			reader.nextToEndElement(CONFIG_ELEMENT);
			return;
		}
		
		while(reader.nextWhileNotEnd(CONFIG_ELEMENT)){
			//extension element
			if(reader.isStartElement() && !DEFAULT_NAMESPACE_URI.equals(reader.getElementName().getNamespaceURI())){
				processExtensionElement(context,reader);
				continue;
			}
			
			if(reader.isStartElement(CONFIG_ELEMENT)){
				readConfig(context, resource, reader);
				reader.next();
				continue;
			}

			if(reader.isStartElement(DEBUG_ELEMENT)){
				String debugValue = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(debugValue)){
					this.debug = Converts.toBoolean(debugValue);
				}
				continue;
			}
			
			if(reader.isStartElement(BASE_PACKAGE_ELEMENT)){
				basePackage = reader.resolveElementTextAndEnd();
				continue;
			}
			
			if(reader.isStartElement(DEFAULT_LOCALE)){
				if(null != defaultLocale){
					throw new AppConfigException("default-locale aleady defined as '" + defaultLocale.toString() + "', duplicated config in xml : " + reader.getSource());
				}
				String defaultLocaleString = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(defaultLocaleString)){
					defaultLocale = Locales.forName(defaultLocaleString);
				}
				continue;
			}
			
			if(reader.isStartElement(DEFAULT_ENCODING)){
				if(null != defaultCharset){
					throw new AppConfigException("default-charset aleady defined as '" + defaultCharset.name() + "', duplicated config in xml : " + reader.getSource());
				}
				String defaultEncodingName = reader.resolveElementTextAndEnd();
				if(!Strings.isEmpty(defaultEncodingName)){
					defaultCharset = Charsets.forName(defaultEncodingName);
				}
				continue;
			}
			
			if(reader.isStartElement(ADDITIONAL_PACKAGES_ELEMENT)) {
				String[] packages = Strings.splitMultiLines(reader.getElementTextAndEnd(), ',');
				if(packages.length > 0) {
					Collections2.addAll(additionalPackages, packages);
				}
				continue;
			}
			
			if(reader.isStartElement(RESOURCES_ELEMENT)){
				if(matchProfile(context.getProfile(), reader)){
					Collections2.addAll(this.resources, Resources.scan(reader.getAttributeRequired(LOCATION_ATTRIBUTE)));
				}
				reader.nextToEndElement(RESOURCES_ELEMENT);
				continue;
			}
			
			if(reader.isStartElement(IMPORT_ELEMENT)){
				if(matchProfile(context.getProfile(), reader)){
					boolean checkExistence    = reader.resolveBooleanAttribute(CHECK_EXISTENCE_ATTRIBUTE, true);
					boolean override          = reader.resolveBooleanAttribute(DEFAULT_OVERRIDE_ATTRIBUTE,context.isDefaultOverried());
					String importResourceName = reader.resolveRequiredAttribute(RESOURCE_ATTRIBUTE);
					
					Resource importResource = Resources.getResource(resource,importResourceName);
					
					if(null == importResource || !importResource.exists()){
						if(checkExistence){
							throw new AppConfigException("the import resource '" + importResourceName + "' not exists");	
						}
					}else{
						loadConfigs(new LoadContext(context.getProfile(), override),importResource);
					}
				}
				reader.nextToEndElement(IMPORT_ELEMENT);
				continue;
			}
			
			if(reader.isStartElement(PROPERTIES_ELEMENT)){
				readProperties(context, resource, reader);
				continue;
			}
			
			if(reader.isStartElement(DATASOURCE_ELEMENT)) {
				readDataSource(context, resource, reader);
				continue;
			}
			
			if(reader.isStartElement(PERMISSIONS_ELEMENT)){
				readPermissions(context, resource, reader);
				continue;
			}
		}
	}
	
	private void processExtensionElement(LoadContext context,XmlReader reader){
		String nsURI = reader.getElementName().getNamespaceURI();
		
		for(AppConfigProcessor extension : processors){

			if(nsURI.equals(extension.getNamespaceURI())){
				extension.processElement(context,reader);
				return;
			}
			
		}
		
		throw new AppConfigException("Namespace uri '" + nsURI + "' not supported, check your config : " + reader.getSource());
	}
	
	private void readDataSource(LoadContext context,Resource resource, XmlReader reader){
		if(!matchProfile(context.getProfile(), reader)){
			reader.nextToEndElement(DATASOURCE_ELEMENT);
			return;
		}
		
		String dataSourceName = reader.getAttribute(NAME_ATTRIBUTE);
		if(Strings.isEmpty(dataSourceName)) {
			dataSourceName = DataSourceManager.DEFAULT_DATASOURCE_NAME;
		}
		
		if(context.hasDataSourceConfig(dataSourceName)) {
			throw new AppConfigException("Found duplicated datasource '" + dataSourceName + "', check your config : " + reader.getSource());
		}
		
		String dataSourceType = reader.getAttribute(TYPE_ATTRIBUTE);
		boolean isDefault     = reader.getBooleanAttribute(DEFAULT_ATTRIBUTE, false);
		
		DataSourceConfig.Builder conf = new DataSourceConfig.Builder();
		conf.setDataSourceType(dataSourceType);
		conf.setDefault(isDefault);
		
		if(isDefault && context.hasDefaultDataSource) {
		    throw new AppConfigException("Found duplicated default datasource'" + dataSourceName + "', check your config : " + reader.getSource());
		}
		
		while(reader.nextWhileNotEnd(DATASOURCE_ELEMENT)){

			if(reader.isStartElement(PROPERTY_ELEMENT)){
				String  name     = reader.resolveRequiredAttribute(NAME_ATTRIBUTE);
				String  value    = reader.resolveAttribute(VALUE_ATTRIBUTE);

				if(Strings.isEmpty(value)){
					value = reader.resolveElementTextAndEnd();
				}else{
					reader.nextToEndElement(PROPERTY_ELEMENT);
				}
				
				conf.setProperty(name, value);
				
				continue;
			}
			
			if(reader.isStartElement()) {
				String name  = reader.getElementLocalName();
				String value = Strings.trim(reader.resolveElementTextAndEnd());
				
				conf.setProperty(name, value);
				continue;
			}
		}
		
		context.setDataSourceConfig(dataSourceName, conf.build());
	}
	
	private void readProperties(LoadContext context,Resource resource, XmlReader reader){
		if(!matchProfile(context.getProfile(), reader)){
			reader.nextToEndElement(PROPERTIES_ELEMENT);
			return;
		}
		
		String prefix = reader.resolveAttribute(PREFIX_ATTRIBUTE);
		if(!Strings.isEmpty(prefix)) {
			char c = prefix.charAt(prefix.length() - 1);
			if(Character.isLetterOrDigit(c)) {
				prefix = prefix + ".";
			}
		}else{
			prefix = Strings.EMPTY;
		}
		
		while(reader.nextWhileNotEnd(PROPERTIES_ELEMENT)){
			if(reader.isStartElement(PROPERTY_ELEMENT)){
				String  name     = reader.resolveRequiredAttribute(NAME_ATTRIBUTE);
				String  value    = reader.resolveAttribute(VALUE_ATTRIBUTE);
				boolean override = reader.resolveBooleanAttribute(OVERRIDE_ATTRIBUTE, context.isDefaultOverried());

				if(Strings.isEmpty(value)){
					value = reader.resolveElementTextAndEnd();
				}else{
					reader.nextToEndElement(PROPERTY_ELEMENT);
				}
				
				if(!override && properties.containsKey(name)){
					throw new AppConfigException("Found duplicated property '" + name + "' in resource : " + resource.getClasspath());
				}
				
				String key = prefix + name;
				
				if(null != propertyProcessor){
					Out<String> newValue = new Out<String>();
					if(propertyProcessor.process(key, value, newValue)) {
						value = newValue.getValue();	
					}
				}
				
				properties.put(prefix + name, value);	
				continue;
			}
		}
	}
	
	private void readPermissions(LoadContext context,Resource resource,XmlReader reader){
		if(!matchProfile(context.getProfile(), reader)){
			reader.nextToEndElement(PERMISSIONS_ELEMENT);
			return;
		}
		
		while(reader.nextWhileNotEnd(PERMISSIONS_ELEMENT)){
			if(reader.isStartElement(GRANT_ELEMENT)){
				readPermission(context, resource, reader, true);
				continue;
			}
			
			if(reader.isStartElement(DENY_ELEMENT)){
				readPermission(context, resource, reader, false);
				continue;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
    private void readPermission(LoadContext context,Resource resource,XmlReader reader,boolean granted){
		String typeName  = reader.resolveAttribute(TYPE_ATTRIBUTE);
		String className = reader.resolveRequiredAttribute(CLASS_ATTRIBUTE);
		String name      = reader.resolveRequiredAttribute(NAME_ATTRIBUTE);
		String actions   = reader.resolveRequiredAttribute(ACTIONS_ATTRIBUTE);
		
		Class<?> permClass = Classes.tryForName(className);
		if(null == permClass){
			throw new AppConfigException("Permission class '" + className + "' not found, source : " + reader.getSource());
		}
		
		if(!SysPermission.class.isAssignableFrom(permClass)){
			throw new AppConfigException("Permission class '" + className + "' must be instanceof '" + SysPermission.class.getName() + "', source : " + reader.getSource());
		}
		
		Class<? extends SysPermission> permType = null;
		if(!Strings.isEmpty(typeName)){
			permType = (Class<? extends SysPermission>)Classes.tryForName(typeName);
			if(null == permType){
				throw new AppConfigException("Permission type class '" + typeName + "' not found, source : " + reader.getSource());
			}
		}else{
			permType = (Class<? extends SysPermission>)permClass;
		}
		
		try {
	        Constructor<?> constructor = permClass.getConstructor(String.class,String.class);
	        
	        SysPermission permObject = (SysPermission)constructor.newInstance(name,actions);
	        
			addPermission(new SysPermissionDefinition(reader.getSource(), permType, permObject, granted),context.isDefaultOverried());
        } catch (NoSuchMethodException e) {
        	throw new AppConfigException("Permission class '" + className + "' must define the consturstor(String.class,String.class), source : " + reader.getSource());
        } catch (Exception e){
        	throw new AppConfigException("Error creating permission instance of class '" + className + ", source : " + reader.getSource(),e);
        }
	}
	
	protected void addPermissions(List<SysPermissionDefinition> permissions,boolean override){
		for(SysPermissionDefinition permission : permissions){
			addPermission(permission, override);
		}
	}
	
	protected void addPermission(SysPermissionDefinition permission,boolean override){
		Map<String,SysPermissionDefinition> typesPermissionsMap = typedPermissions.get(permission.getPermType());
		
		SysPermissionDefinition exists = null;
		
		if(null == typesPermissionsMap){
			typesPermissionsMap = new HashMap<String, SysPermissionDefinition>();
		}else{
			exists = typesPermissionsMap.get(permission.getPermObject().getName());
		}
		
		if(!override && null != exists){
			throw new AppConfigException("Found duplicated permission '" + permission.toString() + "', source : " + permission.getSource() + "," + exists.getSource());
		}
		
		if(null != exists){
			permissions.remove(exists);
		}
		
		typesPermissionsMap.put(permission.getPermObject().getName(), permission);
		permissions.add(permission);
	}
	
	protected static boolean matchProfile(String profile, XmlReader element) {
		String profileName = element.getAttribute(IF_PROFILE_ATTRIBUTE);
		if(!Strings.isEmpty(profileName)){
			return Strings.equalsIgnoreCase(profile, profileName);
		}else{
			return true;
		}
	}
	
	private final class LoadContext extends MapPropertyAccessor implements AppConfigContext{
		protected String  	  profile         = null;
		protected boolean     defaultOverried = false;
		protected boolean     hasDefaultDataSource = false;
		protected Set<String> resources       = new HashSet<String>();
		
		LoadContext(String profile,boolean defaultOverried){
			super(DefaultAppConfigLoader.this.properties);
			this.profile = profile;
			this.defaultOverried = defaultOverried;
		}

		@Override
        public String getProfile() {
	        return profile;
        }
		
		@Override
        public boolean isDefaultOverried() {
	        return defaultOverried;
        }

		@Override
        public Object getExternalContext() {
	        return externalContext;
        }
		
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getExtension(Class<T> type) {
	        return (T)extensions.get(type);
        }

		@Override
        public <T> void setExtension(T extension) {
			Args.notNull(extension);
			extensions.put(extension.getClass(), extension);
        }

		@Override
        public <T> void setExtension(Class<T> type, T extension) {
	        Args.notNull(type);
	        Args.notNull(extension);
	        extensions.put(type, extension);
        }
		
		@Override
        public void addResource(Resource r) {
			if(null != r) {
				DefaultAppConfigLoader.this.resources.add(r);
			}
        }

		@Override
        public void addResources(ResourceSet rs) {
			if(null != rs) {
				for(Resource r : rs.toResourceArray()) {
					DefaultAppConfigLoader.this.resources.add(r);	
				}
			}
		}
		
		@Override
        public boolean hasDefaultDataSourceConfig() {
            return hasDefaultDataSource;
        }

        @Override
        public boolean hasDataSourceConfig(String name) {
	        return dataSourceConfigs.containsKey(name);
        }

		@Override
        public void setDataSourceConfig(String name, DataSourceConfig conf) {
			dataSourceConfigs.put(name, conf);
			
			if(conf.isDefault()) {
		         
	            if (hasDefaultDataSource) {
	                throw new AppConfigException("Default DataSource aleady exists");
	            }
	            
			    this.hasDefaultDataSource = true;
			}
        }

		@Override
        public Map<String, DataSourceConfig> getDataSourceConfigs() {
	        return dataSourceConfigs;
        }
	}
}