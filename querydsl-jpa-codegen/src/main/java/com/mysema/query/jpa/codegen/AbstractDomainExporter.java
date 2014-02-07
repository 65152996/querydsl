/*
 * Copyright 2013, Mysema Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.jpa.codegen;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.Embeddable;
import javax.persistence.Entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mysema.codegen.CodeWriter;
import com.mysema.codegen.JavaWriter;
import com.mysema.codegen.model.Type;
import com.mysema.codegen.model.TypeCategory;
import com.mysema.query.QueryException;
import com.mysema.query.annotations.PropertyType;
import com.mysema.query.annotations.QueryInit;
import com.mysema.query.annotations.QueryType;
import com.mysema.query.codegen.CodegenModule;
import com.mysema.query.codegen.EmbeddableSerializer;
import com.mysema.query.codegen.EntitySerializer;
import com.mysema.query.codegen.EntityType;
import com.mysema.query.codegen.Property;
import com.mysema.query.codegen.QueryTypeFactory;
import com.mysema.query.codegen.Serializer;
import com.mysema.query.codegen.SerializerConfig;
import com.mysema.query.codegen.Supertype;
import com.mysema.query.codegen.SupertypeSerializer;
import com.mysema.query.codegen.TypeFactory;
import com.mysema.query.codegen.TypeMappings;
import com.mysema.util.ReflectionUtils;

/**
 * AbstractDomainExporter is a common supertype for DomainExporters
 *
 * @author tiwe
 *
 */
public abstract class AbstractDomainExporter {

    private static final Logger logger = LoggerFactory.getLogger(HibernateDomainExporter.class);

    private final File targetFolder;

    private final Map<Class<?>, EntityType> allTypes = Maps.newHashMap();

    private final Map<Class<?>, EntityType> entityTypes = Maps.newHashMap();

    private final Map<Class<?>, EntityType> embeddableTypes = Maps.newHashMap();

    private final Map<Class<?>, EntityType> superTypes = Maps.newHashMap();

    private final Set<EntityType> serialized = new HashSet<EntityType>();

    @SuppressWarnings("unchecked")
    protected final TypeFactory typeFactory = new TypeFactory(Arrays.<Class<? extends Annotation>>asList(Entity.class,
            javax.persistence.MappedSuperclass.class, Embeddable.class));

    private final QueryTypeFactory queryTypeFactory;

    private final TypeMappings typeMappings;

    private final Serializer embeddableSerializer;

    private final Serializer entitySerializer;

    private final Serializer supertypeSerializer;

    private final SerializerConfig serializerConfig;

    private final Charset charset;

    private final Set<File> generatedFiles = new HashSet<File>();

    /**
     * @param namePrefix
     * @param nameSuffix
     * @param targetFolder
     * @param serializerConfig
     * @param charset
     */
    public AbstractDomainExporter(String namePrefix, String nameSuffix, File targetFolder,
            SerializerConfig serializerConfig, Charset charset) {
        this.targetFolder = targetFolder;
        this.serializerConfig = serializerConfig;
        this.charset = charset;
        CodegenModule module = new CodegenModule();
        module.bind(CodegenModule.PREFIX, namePrefix);
        module.bind(CodegenModule.SUFFIX, nameSuffix);
        module.bind(CodegenModule.KEYWORDS, Constants.keywords);
        this.queryTypeFactory = module.get(QueryTypeFactory.class);
        this.typeMappings = module.get(TypeMappings.class);
        this.embeddableSerializer = module.get(EmbeddableSerializer.class);
        this.entitySerializer = module.get(EntitySerializer.class);
        this.supertypeSerializer = module.get(SupertypeSerializer.class);
        typeFactory.setUnknownAsEntity(true);
    }

    /**
     * Export the contents
     *
     * @throws IOException
     */
    public void execute() throws IOException {
        // collect types
        try {
            collectTypes();
        } catch (Exception e) {
            throw new QueryException(e);
        }

        // go through supertypes
        Set<Supertype> additions = Sets.newHashSet();
        for (Map.Entry<Class<?>, EntityType> entry : allTypes.entrySet()) {
            EntityType entityType = entry.getValue();
            if (entityType.getSuperType() != null && !allTypes.containsKey(entityType.getSuperType().getType().getJavaClass())) {
                additions.add(entityType.getSuperType());
            }
        }

        for (Supertype type : additions) {
            type.setEntityType(createEntityType(type.getType(), this.superTypes));
        }

        // merge supertype fields into subtypes
        Set<EntityType> handled = new HashSet<EntityType>();
        for (EntityType type : superTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }
        for (EntityType type : entityTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }
        for (EntityType type : embeddableTypes.values()) {
            addSupertypeFields(type, allTypes, handled);
        }

        // serialize them
        serialize(superTypes, supertypeSerializer);
        serialize(embeddableTypes, embeddableSerializer);
        serialize(entityTypes, entitySerializer);
    }

    private void addSupertypeFields(EntityType model, Map<Class<?>, EntityType> superTypes,
            Set<EntityType> handled) {
        if (handled.add(model)) {
            for (Supertype supertype : model.getSuperTypes()) {
                EntityType entityType = superTypes.get(supertype.getType().getJavaClass());
                if (entityType != null) {
                    addSupertypeFields(entityType, superTypes, handled);
                    supertype.setEntityType(entityType);
                    model.include(supertype);
                }
            }
        }
    }

    protected abstract void collectTypes() throws Exception;

    protected EntityType createEmbeddableType(Class<?> cl) {
        return createEntityType(cl, embeddableTypes);
    }

    protected EntityType createEmbeddableType(Type type) {
        return createEntityType(type, embeddableTypes);
    }

    protected EntityType createEntityType(Class<?> cl) {
        return createEntityType(cl, entityTypes);
    }

    private EntityType createEntityType(Class<?> cl,  Map<Class<?>, EntityType> types) {
        if (allTypes.containsKey(cl)) {
            return allTypes.get(cl);
        } else {
            EntityType type = typeFactory.getEntityType(cl);
            typeMappings.register(type, queryTypeFactory.create(type));
            if (!cl.getSuperclass().equals(Object.class)) {
                type.addSupertype(new Supertype(typeFactory.get(cl.getSuperclass(), cl.getGenericSuperclass())));
            }
            types.put(cl, type);
            allTypes.put(cl, type);
            return type;
        }
    }

    protected EntityType createEntityType(Type type) {
        return createEntityType(type, entityTypes);
    }

    protected EntityType createEntityType(Type type,  Map<Class<?>, EntityType> types) {
        Class<?> key = type.getJavaClass();
        if (allTypes.containsKey(key)) {
            return allTypes.get(key);
        } else {
            EntityType entityType = new EntityType(type);
            typeMappings.register(entityType, queryTypeFactory.create(entityType));
            Class<?> superClass = key.getSuperclass();
            if (entityType.getSuperType() == null && superClass != null && !superClass.equals(Object.class)) {
                entityType.addSupertype(new Supertype(typeFactory.get(superClass, key.getGenericSuperclass())));
            }
            types.put(key, entityType);
            allTypes.put(key, entityType);
            return entityType;
        }
    }

    @Nullable
    protected Property createProperty(EntityType entityType, String propertyName, Type propertyType,
            AnnotatedElement annotated) {
        List<String> inits = Collections.<String>emptyList();
        if (annotated.isAnnotationPresent(QueryInit.class)) {
            inits = ImmutableList.copyOf(annotated.getAnnotation(QueryInit.class).value());
        }
        if (annotated.isAnnotationPresent(QueryType.class)) {
            QueryType queryType = annotated.getAnnotation(QueryType.class);
            if (queryType.value().equals(PropertyType.NONE)) {
                return null;
            }
            propertyType = propertyType.as(TypeCategory.valueOf(queryType.value().name()));
        }
        return new Property(entityType, propertyName, propertyType, inits);
    }

    protected EntityType createSuperType(Class<?> cl) {
        return createEntityType(cl, superTypes);
    }

    protected AnnotatedElement getAnnotatedElement(Class<?> cl, String propertyName) throws NoSuchMethodException {
        Field field = ReflectionUtils.getFieldOrNull(cl, propertyName);
        if (field != null) {
            return field;
        } else {
            Method method = ReflectionUtils.getGetterOrNull(cl, propertyName);
            if (method != null) {
                return method;
            } else {
                throw new IllegalArgumentException("No property found for " + cl.getName() + "." + propertyName);
            }
        }
    }

    public Set<File> getGeneratedFiles() {
        return generatedFiles;
    }

    protected Type getType(Class<?> cl, Class<?> mappedType, String propertyName) throws NoSuchMethodException {
        Field field = ReflectionUtils.getFieldOrNull(cl, propertyName);
        if (field != null) {
            if (mappedType.isAssignableFrom(field.getType())) {
                return typeFactory.get(field.getType(), field.getGenericType());
            } else {
                return typeFactory.get(mappedType);
            }
        } else {
            Method method = ReflectionUtils.getGetterOrNull(cl, propertyName);
            if (method != null) {
                if (mappedType.isAssignableFrom(method.getReturnType())) {
                    return typeFactory.get(method.getReturnType(), method.getGenericReturnType());
                } else {
                    return typeFactory.get(mappedType);
                }
            } else {
                throw new IllegalArgumentException("No property found for " + cl.getName() + "." + propertyName);
            }
        }
    }

    private void serialize(Map<Class<?>, EntityType> types, Serializer serializer) throws IOException {
        for (EntityType entityType : types.values()) {
            if (serialized.add(entityType)) {
                Type type = typeMappings.getPathType(entityType, entityType, true);
                String packageName = type.getPackageName();
                String className = packageName.length() > 0 ? (packageName + "." + type.getSimpleName()) : type.getSimpleName();
                write(serializer, className.replace('.', '/') + ".java", entityType);
            }
        }
    }

    private void write(Serializer serializer, String path, EntityType type) throws IOException {
        File targetFile = new File(targetFolder, path);
        generatedFiles.add(targetFile);
        Writer w = writerFor(targetFile);
        try{
            CodeWriter writer = new JavaWriter(w);
            serializer.serialize(type, serializerConfig, writer);
        }finally{
            w.close();
        }
    }

    private Writer writerFor(File file) {
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            logger.error("Folder " + file.getParent() + " could not be created");
        }
        try {
            return new OutputStreamWriter(new FileOutputStream(file), charset);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}