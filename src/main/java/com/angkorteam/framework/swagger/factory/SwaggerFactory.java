package com.angkorteam.framework.swagger.factory;

import com.angkorteam.framework.swagger.*;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Created by socheat on 11/23/15.
 */
public class SwaggerFactory implements FactoryBean<Swagger>, InitializingBean {

    private Swagger swagger;

    private String title;

    private String description;

    private String version;

    private String termsOfService;

    private io.swagger.models.Contact contact;

    private License license;

    private String basePath;

    private List<String> consumes;

    private List<String> produces = Arrays.asList(MediaType.APPLICATION_JSON_VALUE);

    private String host;

    private ExternalDocs externalDocs;

    private List<Scheme> schemes = Arrays.asList(Scheme.HTTP);

    private String[] resourcePackages;

    @Override
    public Swagger getObject() throws Exception {
        return this.swagger;
    }

    @Override
    public Class<?> getObjectType() {
        return Swagger.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Swagger swagger = new Swagger();
        io.swagger.models.Info info = new io.swagger.models.Info();
        swagger.setInfo(info);
        info.setTitle(title);
        info.setLicense(license);
        info.setContact(contact);
        info.setTermsOfService(termsOfService);
        info.setVersion(version);
        info.setDescription(description);

        swagger.setConsumes(consumes);
        swagger.setProduces(produces);
        swagger.setSchemes(schemes);
        swagger.setBasePath(basePath);
        swagger.setHost(host);
        swagger.setExternalDocs(externalDocs);

        ConfigurationBuilder config = new ConfigurationBuilder();
        Set<String> acceptablePackages = new HashSet<>();

        boolean allowAllPackages = false;

        if (resourcePackages != null && resourcePackages.length > 0) {
            for (String resourcePackage : resourcePackages) {
                if (resourcePackage != null && !"".equals(resourcePackage)) {
                    acceptablePackages.add(resourcePackage);
                    config.addUrls(ClasspathHelper.forPackage(resourcePackage));
                }
            }
        }

        config.setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner());

        Reflections reflections = new Reflections(config);
        Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);

        Set<Class<?>> output = new HashSet<Class<?>>();
        for (Class<?> cls : controllers) {
            if (allowAllPackages) {
                output.add(cls);
            } else {
                for (String pkg : acceptablePackages) {
                    if (cls.getPackage().getName().startsWith(pkg)) {
                        output.add(cls);
                    }
                }
            }
        }

        Map<String, Path> paths = new HashMap<>();
        swagger.setPaths(paths);
        Map<String, Model> definitions = new HashMap<>();
        swagger.setDefinitions(definitions);
        Stack<Class<?>> modelStack = new Stack<>();
        for (Class<?> controller : controllers) {
            List<String> clazzPaths = new ArrayList<>();
            RequestMapping clazzRequestMapping = controller.getDeclaredAnnotation(RequestMapping.class);
            Api api = controller.getDeclaredAnnotation(Api.class);
            if (clazzRequestMapping != null) {
                clazzPaths = lookPaths(clazzRequestMapping);
            }
            if (clazzPaths.isEmpty()) {
                clazzPaths.add("");
            }
            if (api != null) {
                if (!"".equals(api.description())) {
                    for (String name : api.tags()) {
                        if (!"".equals(name)) {
                            io.swagger.models.Tag tag = new io.swagger.models.Tag();
                            tag.setDescription(api.description());
                            tag.setName(name);
                            swagger.addTag(tag);
                        }
                    }
                }
            } else {
                io.swagger.models.Tag tag = new io.swagger.models.Tag();
                tag.setDescription("Unknown");
                tag.setName("Unknown");
                swagger.addTag(tag);
            }
            Method[] methods = null;
            try {
                methods = controller.getDeclaredMethods();
            } catch (NoClassDefFoundError e) {
            }
            if (methods != null && methods.length > 0) {
                for (Method method : methods) {
                    RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                    ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
                    ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
                    ApiHeaders apiHeaders = method.getAnnotation(ApiHeaders.class);
                    List<String> methodPaths = new ArrayList<>();
                    if (requestMapping != null && apiOperation != null && apiResponses != null) {
                        methodPaths = lookPaths(requestMapping);
                    }
                    if (methodPaths.isEmpty()) {
                        methodPaths.add("");
                    }
                    if (requestMapping != null && apiOperation != null && apiResponses != null) {
                        for (String classPath : clazzPaths) {
                            for (String methodPath : methodPaths) {
                                RequestMethod[] requestMethods = requestMapping.method();
                                if (requestMethods == null || requestMethods.length == 0) {
                                    requestMethods = RequestMethod.values();
                                }
                                Path path = new Path();
                                paths.put(classPath + methodPath, path);
                                for (RequestMethod requestMethod : requestMethods) {
                                    Operation operation = new Operation();
                                    operation.setDescription(apiOperation.description());
                                    for (String consume : requestMapping.consumes()) {
                                        operation.addConsumes(consume);
                                    }
                                    for (String produce : requestMapping.produces()) {
                                        operation.addProduces(produce);
                                    }
                                    if (api != null) {
                                        if (!"".equals(api.description())) {
                                            for (String name : api.tags()) {
                                                if (!"".equals(name)) {
                                                    operation.addTag(name);
                                                }
                                            }
                                        }
                                    } else {
                                        io.swagger.models.Tag tag = new io.swagger.models.Tag();
                                        operation.addTag("Unknown");
                                    }

                                    if (requestMethod == RequestMethod.DELETE) {
                                        path.delete(operation);
                                    } else if (requestMethod == RequestMethod.GET) {
                                        path.get(operation);
                                    } else if (requestMethod == RequestMethod.HEAD) {
                                        path.head(operation);
                                    } else if (requestMethod == RequestMethod.OPTIONS) {
                                        path.options(operation);
                                    } else if (requestMethod == RequestMethod.PATCH) {
                                        path.patch(operation);
                                    } else if (requestMethod == RequestMethod.POST) {
                                        path.post(operation);
                                    } else if (requestMethod == RequestMethod.PUT) {
                                        path.put(operation);
                                    }

                                    if (apiHeaders != null && apiHeaders.value() != null && apiHeaders.value().length > 0) {
                                        for (ApiHeader header : apiHeaders.value()) {
                                            HeaderParameter parameter = new HeaderParameter();
                                            parameter.setName(header.name());
                                            parameter.setType("string");
                                            parameter.setDescription(header.description());
                                            parameter.setRequired(header.required());
                                            operation.addParameter(parameter);
                                        }
                                    }

                                    for (Parameter parameter : method.getParameters()) {
                                        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                                        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                                        RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
                                        RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
                                        ApiParam apiParam = parameter.getAnnotation(ApiParam.class);
                                        if (apiParam != null && pathVariable != null && isSimpleScalar(parameter.getType())) {
                                            PathParameter pathParameter = new PathParameter();
                                            pathParameter.setRequired(true);
                                            pathParameter.setDescription(apiParam.description());
                                            pathParameter.setType(lookupType(parameter.getType()));
                                            pathParameter.setFormat(lookupFormat(parameter.getType(), apiParam));
                                            pathParameter.setName(pathVariable.value());
                                            operation.addParameter(pathParameter);
                                            continue;
                                        }

                                        if (requestMethod == RequestMethod.DELETE
                                                || requestMethod == RequestMethod.GET
                                                || requestMethod == RequestMethod.HEAD
                                                || requestMethod == RequestMethod.OPTIONS
                                                || requestMethod == RequestMethod.PATCH
                                                || requestMethod == RequestMethod.PUT) {
                                            if (apiParam != null && requestParam != null && isSimpleArray(parameter.getType())) {
                                                QueryParameter param = new QueryParameter();
                                                param.setRequired(requestParam.required());
                                                param.setDescription(apiParam.description());
                                                param.setType("array");
                                                if (!"".equals(requestParam.value())) {
                                                    param.setName(requestParam.value());
                                                }
                                                if (!"".equals(requestParam.name())) {
                                                    param.setName(requestParam.name());
                                                }
                                                param.setItems(lookupProperty(parameter.getType(), requestParam, apiParam));
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestParam != null && isSimpleScalar(parameter.getType())) {
                                                QueryParameter param = new QueryParameter();
                                                param.setRequired(requestParam.required());
                                                param.setDescription(apiParam.description());
                                                param.setType(lookupType(parameter.getType()));
                                                param.setFormat(lookupFormat(parameter.getType(), apiParam));
                                                if (!"".equals(requestParam.value())) {
                                                    param.setName(requestParam.value());
                                                }
                                                if (!"".equals(requestParam.name())) {
                                                    param.setName(requestParam.name());
                                                }
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestBody != null && parameter.getType() == MultipartFile.class) {
                                                FormParameter param = new FormParameter();
                                                param.setRequired(true);
                                                param.setIn("body");
                                                param.setName("body");
                                                param.setType("file");
                                                param.setDescription(apiParam.description());
                                                operation.addConsumes("application/octet-stream");
//                                                BodyParameter param = new BodyParameter();
//                                                param.setRequired(requestBody.required());
//                                                param.setDescription(apiParam.description());
//                                                param.setName("body");
//                                                ModelImpl model = new ModelImpl();
//                                                model.setType("file");
//                                                param.setSchema(model);
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestBody != null && isSimpleArray(parameter.getType())) {
                                                BodyParameter param = new BodyParameter();
                                                param.setRequired(requestBody.required());
                                                param.setDescription(apiParam.description());
                                                param.setName("body");
                                                ArrayModel model = new ArrayModel();
                                                StringProperty property = new StringProperty();
                                                property.setType(lookupType(parameter.getType()));
                                                property.setFormat(lookupFormat(parameter.getType(), apiParam));
                                                model.setItems(property);
                                                param.setSchema(model);
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestBody != null && isSimpleScalar(parameter.getType())) {
                                                BodyParameter param = new BodyParameter();
                                                param.setRequired(requestBody.required());
                                                param.setDescription(apiParam.description());
                                                param.setName("body");
                                                ModelImpl model = new ModelImpl();
                                                model.setType(lookupType(parameter.getType()));
                                                model.setFormat(lookupFormat(parameter.getType(), apiParam));
                                                param.setSchema(model);
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestBody != null && isModelArray(parameter.getType())) {
                                                BodyParameter param = new BodyParameter();
                                                param.setRequired(requestBody.required());
                                                param.setDescription(apiParam.description());
                                                param.setName("body");
                                                ArrayModel model = new ArrayModel();
                                                RefProperty property = new RefProperty();
                                                property.setType(lookupType(parameter.getType()));
                                                property.set$ref("#/definitions/" + parameter.getType().getComponentType().getSimpleName());
                                                if (!modelStack.contains(parameter.getType().getComponentType())) {
                                                    modelStack.push(parameter.getType().getComponentType());
                                                }
                                                model.setItems(property);
                                                param.setSchema(model);
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestBody != null && isModelScalar(parameter.getType())) {
                                                BodyParameter param = new BodyParameter();
                                                param.setRequired(requestBody.required());
                                                param.setDescription(apiParam.description());
                                                param.setName("body");
                                                RefModel model = new RefModel();
                                                model.set$ref("#/definitions/" + parameter.getType().getSimpleName());
                                                if (!modelStack.contains(parameter.getType())) {
                                                    modelStack.push(parameter.getType());
                                                }
                                                param.setSchema(model);
                                                operation.addParameter(param);
                                                continue;
                                            }
                                        } else if (requestMethod == RequestMethod.POST) {
                                            if (apiParam != null && requestParam != null && isSimpleArray(parameter.getType())) {
                                                FormParameter param = new FormParameter();
                                                param.setRequired(requestParam.required());
                                                param.setDescription(apiParam.description());
                                                param.setType("array");
                                                if (!"".equals(requestParam.value())) {
                                                    param.setName(requestParam.value());
                                                }
                                                if (!"".equals(requestParam.name())) {
                                                    param.setName(requestParam.name());
                                                }
                                                param.setItems(lookupProperty(parameter.getType(), requestParam, apiParam));
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestParam != null && isSimpleScalar(parameter.getType())) {
                                                FormParameter param = new FormParameter();
                                                param.setRequired(requestParam.required());
                                                param.setDescription(apiParam.description());
                                                param.setType(lookupType(parameter.getType()));
                                                param.setFormat(lookupFormat(parameter.getType(), apiParam));
                                                if (!"".equals(requestParam.value())) {
                                                    param.setName(requestParam.value());
                                                }
                                                if (!"".equals(requestParam.name())) {
                                                    param.setName(requestParam.name());
                                                }
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestPart != null && isSimpleArray(parameter.getType())) {
                                                FormParameter param = new FormParameter();
                                                param.setRequired(requestPart.required());
                                                param.setDescription(apiParam.description());
                                                param.setType("array");
                                                if (!"".equals(requestPart.value())) {
                                                    param.setName(requestPart.value());
                                                }
                                                if (!"".equals(requestPart.name())) {
                                                    param.setName(requestPart.name());
                                                }
                                                param.setItems(lookupProperty(parameter.getType(), requestParam, apiParam));
                                                operation.addParameter(param);
                                                continue;
                                            }
                                            if (apiParam != null && requestPart != null && isSimpleScalar(parameter.getType())) {
                                                FormParameter param = new FormParameter();
                                                param.setRequired(requestPart.required());
                                                param.setDescription(apiParam.description());
                                                param.setType(lookupType(parameter.getType()));
                                                param.setFormat(lookupFormat(parameter.getType(), apiParam));
                                                if (!"".equals(requestPart.value())) {
                                                    param.setName(requestPart.value());
                                                }
                                                if (!"".equals(requestPart.name())) {
                                                    param.setName(requestPart.name());
                                                }
                                                operation.addParameter(param);
                                                continue;
                                            }
                                        }
                                    }

                                    for (ApiResponse apiResponse : apiResponses.value()) {
                                        if (isSimpleScalar(apiResponse.response())) {
                                            if (apiResponse.array()) {
                                                Response response = new Response();
                                                if (!"".equals(apiResponse.description())) {
                                                    response.setDescription(apiResponse.httpStatus().getReasonPhrase());
                                                } else {
                                                    response.setDescription(apiResponse.description());
                                                }
                                                ArrayProperty property = new ArrayProperty();
                                                property.setItems(lookupProperty(apiResponse.response(), apiResponse));
                                                response.setSchema(property);
                                                operation.addResponse(String.valueOf(apiResponse.httpStatus().value()), response);
                                            } else {
                                                Response response = new Response();
                                                if ("".equals(apiResponse.description())) {
                                                    response.setDescription(apiResponse.httpStatus().getReasonPhrase());
                                                } else {
                                                    response.setDescription(apiResponse.description());
                                                }
                                                response.setSchema(lookupProperty(apiResponse.response(), apiResponse));
                                                operation.addResponse(String.valueOf(apiResponse.httpStatus().value()), response);
                                            }
                                        } else if (isModelScalar(apiResponse.response())) {
                                            if (apiResponse.array()) {
                                                Response response = new Response();
                                                if (!"".equals(apiResponse.description())) {
                                                    response.setDescription(apiResponse.httpStatus().getReasonPhrase());
                                                } else {
                                                    response.setDescription(apiResponse.description());
                                                }
                                                RefProperty property = new RefProperty();
                                                property.set$ref("#/definitions/" + apiResponse.response().getSimpleName());
                                                if (!modelStack.contains(apiResponse.response())) {
                                                    modelStack.push(apiResponse.response());
                                                }
                                                ArrayProperty array = new ArrayProperty();
                                                array.setItems(property);
                                                response.setSchema(array);
                                                operation.addResponse(String.valueOf(apiResponse.httpStatus().value()), response);
                                            } else {
                                                Response response = new Response();
                                                if (!"".equals(apiResponse.description())) {
                                                    response.setDescription(apiResponse.httpStatus().getReasonPhrase());
                                                } else {
                                                    response.setDescription(apiResponse.description());
                                                }
                                                RefProperty property = new RefProperty();
                                                property.set$ref("#/definitions/" + apiResponse.response().getSimpleName());
                                                if (!modelStack.contains(apiResponse.response())) {
                                                    modelStack.push(apiResponse.response());
                                                }
                                                response.setSchema(property);
                                                operation.addResponse(String.valueOf(apiResponse.httpStatus().value()), response);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        while (!modelStack.isEmpty()) {
            Class<?> scheme = modelStack.pop();
            if (definitions.containsKey(scheme.getSimpleName())) {
                continue;
            }
            java.lang.reflect.Field[] fields = scheme.getDeclaredFields();
            if (fields != null && fields.length > 0) {
                ModelImpl model = new ModelImpl();
                model.setType("object");
                for (Field field : fields) {
                    ApiProperty apiProperty = field.getDeclaredAnnotation(ApiProperty.class);
                    if (apiProperty != null) {
                        if (apiProperty.array()) {
                            Class<?> type = apiProperty.model();
                            ArrayProperty property = new ArrayProperty();
                            if (isSimpleScalar(type)) {
                                property.setItems(lookupProperty(type, apiProperty));
                            } else if (isModelScalar(type)) {
                                if (!definitions.containsKey(type.getSimpleName())) {
                                    modelStack.push(type);
                                }
                                RefProperty ref = new RefProperty();
                                ref.set$ref("#/definitions/" + type.getSimpleName());
                                property.setItems(ref);
                            }
                            model.addProperty(field.getName(), property);
                        } else {
                            Class<?> type = field.getType();
                            if (isSimpleScalar(type)) {
                                model.addProperty(field.getName(), lookupProperty(type, apiProperty));
                            } else if (isModelScalar(type)) {
                                if (!definitions.containsKey(type.getSimpleName())) {
                                    modelStack.push(type);
                                }
                                RefProperty ref = new RefProperty();
                                ref.set$ref("#/definitions/" + type.getSimpleName());
                                model.addProperty(field.getName(), ref);
                            }
                        }
                    }
                }
                definitions.put(scheme.getSimpleName(), model);
            }
        }

        this.swagger = swagger;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTermsOfService() {
        return termsOfService;
    }

    public void setTermsOfService(String termsOfService) {
        this.termsOfService = termsOfService;
    }

    public io.swagger.models.Contact getContact() {
        return contact;
    }

    public void setContact(io.swagger.models.Contact contact) {
        this.contact = contact;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public void setConsumes(List<String> consumes) {
        this.consumes = consumes;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public ExternalDocs getExternalDocs() {
        return externalDocs;
    }

    public void setExternalDocs(ExternalDocs externalDocs) {
        this.externalDocs = externalDocs;
    }

    public List<Scheme> getSchemes() {
        return schemes;
    }

    public void setSchemes(List<Scheme> schemes) {
        this.schemes = schemes;
    }

    public String[] getResourcePackages() {
        return resourcePackages;
    }

    public void setResourcePackages(String[] resourcePackages) {
        this.resourcePackages = resourcePackages;
    }

    protected static List<String> lookPaths(RequestMapping requestMapping) {
        List<String> paths = new ArrayList<>();
        if (requestMapping != null) {
            if (requestMapping.value() != null && requestMapping.value().length > 0) {
                for (String value : requestMapping.value()) {
                    if (!"".equals(value) && !"/".equals(value) && !paths.contains(value)) {
                        paths.add(value);
                    }
                }
            }
            if (requestMapping.path() != null && requestMapping.path().length > 0) {
                for (String path : requestMapping.path()) {
                    if (!"".equals(path) && !"/".equals(path) && !paths.contains(path)) {
                        paths.add(path);
                    }
                }
            }
        }
        return paths;
    }

    public static Property lookupProperty(Class<?> type, ApiProperty apiProperty) {
        if (type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == int.class || type == Integer.class ||
                type == short.class || type == Short.class) {
            IntegerProperty property = new IntegerProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == long.class || type == Long.class ||
                type == long[].class || type == Long[].class) {
            LongProperty property = new LongProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == float.class || type == Float.class ||
                type == float[].class || type == Float[].class) {
            FloatProperty property = new FloatProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == double.class || type == Double.class ||
                type == double[].class || type == Double[].class) {
            DoubleProperty property = new DoubleProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == String.class || type == String[].class) {
            if (apiProperty.date()) {
                DateProperty property = new DateProperty();
                property.setDescription(apiProperty.description());
                return property;
            } else if (apiProperty.dateTime()) {
                DateTimeProperty property = new DateTimeProperty();
                property.setDescription(apiProperty.description());
                return property;
            } else if (apiProperty.password()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiProperty.description());
                property.setFormat("password");
                return property;
            } else if (apiProperty.email()) {
                EmailProperty property = new EmailProperty();
                property.setDescription(apiProperty.description());
                return property;
            } else if (apiProperty.url()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiProperty.description());
                property.setFormat("url");
                return property;
            } else if (apiProperty.uri()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiProperty.description());
                property.setFormat("uri");
                return property;
            } else {
                StringProperty property = new StringProperty();
                property.setDescription(apiProperty.description());
                return property;
            }
        } else if (type == byte.class || type == Byte.class) {
            StringProperty property = new StringProperty();
            property.setDescription(apiProperty.description());
            property.setFormat("byte");
            return property;
        } else if (type == byte[].class || type == Byte[].class) {
            ByteArrayProperty property = new ByteArrayProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == boolean.class || type == Boolean.class ||
                type == boolean[].class || type == Boolean[].class) {
            BooleanProperty property = new BooleanProperty();
            property.setDescription(apiProperty.description());
            return property;
        } else if (type == MultipartFile.class || type == MultipartFile[].class) {
            FileProperty property = new FileProperty();
            property.setDescription(apiProperty.description());
            return property;
        }
        return null;
    }

    public static Property lookupProperty(Class<?> type, ApiResponse apiResponse) {
        if (type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == int.class || type == Integer.class ||
                type == short.class || type == Short.class) {
            IntegerProperty property = new IntegerProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == long.class || type == Long.class ||
                type == long[].class || type == Long[].class) {
            LongProperty property = new LongProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == float.class || type == Float.class ||
                type == float[].class || type == Float[].class) {
            FloatProperty property = new FloatProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == double.class || type == Double.class ||
                type == double[].class || type == Double[].class) {
            DoubleProperty property = new DoubleProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == String.class || type == String[].class) {
            if (apiResponse.date()) {
                DateProperty property = new DateProperty();
                property.setDescription(apiResponse.description());
                return property;
            } else if (apiResponse.dateTime()) {
                DateTimeProperty property = new DateTimeProperty();
                property.setDescription(apiResponse.description());
                return property;
            } else if (apiResponse.password()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiResponse.description());
                property.setFormat("password");
                return property;
            } else if (apiResponse.email()) {
                EmailProperty property = new EmailProperty();
                property.setDescription(apiResponse.description());
                return property;
            } else if (apiResponse.url()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiResponse.description());
                property.setFormat("url");
                return property;
            } else if (apiResponse.uri()) {
                StringProperty property = new StringProperty();
                property.setDescription(apiResponse.description());
                property.setFormat("uri");
                return property;
            } else {
                StringProperty property = new StringProperty();
                property.setDescription(apiResponse.description());
                return property;
            }
        } else if (type == byte.class || type == Byte.class) {
            StringProperty property = new StringProperty();
            property.setDescription(apiResponse.description());
            property.setFormat("byte");
            return property;
        } else if (type == byte[].class || type == Byte[].class) {
            ByteArrayProperty property = new ByteArrayProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == boolean.class || type == Boolean.class ||
                type == boolean[].class || type == Boolean[].class) {
            BooleanProperty property = new BooleanProperty();
            property.setDescription(apiResponse.description());
            return property;
        } else if (type == MultipartFile.class || type == MultipartFile[].class) {
            FileProperty property = new FileProperty();
            property.setDescription(apiResponse.description());
            return property;
        }
        return null;
    }


    public static String lookupType(Class<?> type) {
        if (type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == long.class || type == Long.class ||
                type == long[].class || type == Long[].class) {
            return "integer";
        } else if (type == float.class || type == Float.class ||
                type == float[].class || type == Float[].class ||
                type == double.class || type == Double.class ||
                type == double[].class || type == Double[].class) {
            return "number";
        } else if (type == String.class || type == String[].class ||
                type == byte.class || type == Byte.class ||
                type == byte[].class || type == Byte[].class) {
            return "string";
        } else if (type == boolean.class || type == Boolean.class ||
                type == boolean[].class || type == Boolean[].class) {
            return "boolean";
        } else if (type == MultipartFile.class || type == MultipartFile[].class) {
            return "file";
        }
        return "";
    }

    public static Property lookupProperty(Class<?> type, RequestParam requestParam, ApiParam apiParam) {
        if (type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == int.class || type == Integer.class ||
                type == short.class || type == Short.class) {
            IntegerProperty property = new IntegerProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == long.class || type == Long.class ||
                type == long[].class || type == Long[].class) {
            LongProperty property = new LongProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == float.class || type == Float.class ||
                type == float[].class || type == Float[].class) {
            FloatProperty property = new FloatProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == double.class || type == Double.class ||
                type == double[].class || type == Double[].class) {
            DoubleProperty property = new DoubleProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == String.class || type == String[].class) {
            if (apiParam.date()) {
                DateProperty property = new DateProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                return property;
            } else if (apiParam.dateTime()) {
                DateTimeProperty property = new DateTimeProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                return property;
            } else if (apiParam.password()) {
                StringProperty property = new StringProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                property.setFormat("password");
                return property;
            } else if (apiParam.email()) {
                EmailProperty property = new EmailProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                return property;
            } else if (apiParam.url()) {
                StringProperty property = new StringProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                property.setFormat("url");
                return property;
            } else if (apiParam.uri()) {
                StringProperty property = new StringProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                property.setFormat("uri");
                return property;
            } else {
                StringProperty property = new StringProperty();
                property.setName(requestParam.name());
                property.setDescription(apiParam.description());
                return property;
            }
        } else if (type == byte.class || type == Byte.class) {
            StringProperty property = new StringProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            property.setFormat("byte");
            return property;
        } else if (type == byte[].class || type == Byte[].class) {
            ByteArrayProperty property = new ByteArrayProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == boolean.class || type == Boolean.class ||
                type == boolean[].class || type == Boolean[].class) {
            BooleanProperty property = new BooleanProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        } else if (type == MultipartFile.class || type == MultipartFile[].class) {
            FileProperty property = new FileProperty();
            property.setName(requestParam.name());
            property.setDescription(apiParam.description());
            return property;
        }
        return null;
    }

    public static String lookupFormat(Class<?> type, ApiParam apiParam) {
        if (type == int.class || type == Integer.class ||
                type == short.class || type == Short.class ||
                type == int.class || type == Integer.class ||
                type == short.class || type == Short.class) {
            return "int32";
        } else if (type == long.class || type == Long.class ||
                type == long[].class || type == Long[].class) {
            return "int64";
        } else if (type == float.class || type == Float.class ||
                type == float[].class || type == Float[].class) {
            return "float";
        } else if (type == double.class || type == Double.class ||
                type == double[].class || type == Double[].class) {
            return "double";
        } else if (type == String.class || type == String[].class) {
            if (apiParam.date()) {
                return "date";
            } else if (apiParam.dateTime()) {
                return "date-time";
            } else if (apiParam.password()) {
                return "password";
            } else if (apiParam.email()) {
                return "email";
            } else if (apiParam.uri()) {
                return "uri";
            } else if (apiParam.url()) {
                return "url";
            }
        } else if (type == byte.class || type == Byte.class) {
            return "byte";
        } else if (type == byte[].class || type == Byte[].class) {
            return "binary";
        } else if (type == boolean.class || type == Boolean.class ||
                type == boolean[].class || type == Boolean[].class) {
            return "";
        }
        return "";
    }

    public static boolean isSimpleArray(Class<?> type) {
        return type.isArray() && (type == byte[].class || type == Byte[].class
                || type == short[].class || type == Short[].class
                || type == int[].class || type == Integer[].class
                || type == float[].class || type == Float[].class
                || type == long[].class || type == Long[].class
                || type == double[].class || type == Double[].class
                || type == boolean[].class || type == Boolean[].class
                || type == char[].class || type == Character[].class
                || type == MultipartFile[].class
                || type == String[].class
        );
    }

    public static boolean isSimpleScalar(Class<?> type) {
        return !type.isArray() && (type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == float.class || type == Float.class
                || type == long.class || type == Long.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class
                || type == MultipartFile.class
                || type == String.class
        );
    }

    public static boolean isModelArray(Class<?> type) {
        return type.isArray() && (type != byte[].class && type != Byte[].class
                && type != short[].class && type != Short[].class
                && type != int[].class && type != Integer[].class
                && type != float[].class && type != Float[].class
                && type != long[].class && type != Long[].class
                && type != double[].class && type != Double[].class
                && type != boolean[].class && type != Boolean[].class
                && type != char[].class && type != Character[].class
                && type != Date[].class
                && type != MultipartFile[].class
                && type != String[].class
                && type.getComponentType().getAnnotation(ApiModel.class) != null
        );
    }

    public static boolean isModelScalar(Class<?> type) {
        return !type.isArray() && (type != byte.class && type != Byte.class
                && type != short.class && type != Short.class
                && type != int.class && type != Integer.class
                && type != float.class && type != Float.class
                && type != long.class && type != Long.class
                && type != double.class && type != Double.class
                && type != boolean.class && type != Boolean.class
                && type != char.class && type != Character.class
                && type != Date.class
                && type != MultipartFile.class
                && type != String.class
                && type.getAnnotation(ApiModel.class) != null
        );
    }
}
