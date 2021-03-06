/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.auraframework.impl.system;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.auraframework.Aura;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.Definition;
import org.auraframework.impl.util.AuraUtil;
import org.auraframework.impl.util.TypeParser;
import org.auraframework.impl.util.TypeParser.Type;
import org.auraframework.service.ContextService;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.json.Json;

/**
 */
public class DefDescriptorImpl<T extends Definition> implements DefDescriptor<T> {
    // Beware, here be dragons!
    // Any change to DefType (below) necessitates a serialVersionUID update 
    private static final long serialVersionUID = 4887575216081546450L;

    private final DefDescriptor<?> bundle;
    protected final String namespace;
    protected final String name;
    protected final String qualifiedName;
    protected final String descriptorName;
    protected final String prefix;
    protected final String nameParameters;
    protected final DefType defType;

    private final int hashCode;

    public DefDescriptorImpl(DefDescriptor<?> associate, Class<T> defClass, String newPrefix) {
        this.defType = DefType.getDefType(defClass);
        this.prefix = newPrefix;
        this.name = associate.getName();
        this.namespace = associate.getNamespace();
        this.qualifiedName = buildQualifiedName(prefix, namespace, name);
        this.descriptorName = buildDescriptorName(prefix, namespace, name);
        int pos = name.indexOf('<');
        this.nameParameters = pos >= 0 ? name.substring(pos).replaceAll("\\s", "") : null;
        // FIXME This is a bit ugly.
        if (this.defType == DefType.TESTSUITE) {
            this.bundle = associate;
        } else {
            this.bundle = null;
        }
        this.hashCode = createHashCode();
    }

    public DefDescriptorImpl(String prefix, String namespace, String name, Class<T> defClass) {
        this(prefix, namespace, name, defClass, null);
    }

    public DefDescriptorImpl(String prefix, String namespace, String name, Class<T> defClass, DefDescriptor<?> bundle) {
        this.defType = DefType.getDefType(defClass);
        int pos = name.indexOf('<');
        this.nameParameters = pos >= 0 ? name.substring(pos).replaceAll("\\s", "") : null;

        this.prefix = prefix;
        this.qualifiedName = buildQualifiedName(prefix, namespace, name);
        this.descriptorName = buildDescriptorName(prefix, namespace, name);
        this.namespace = namespace;
        this.name = name;
        // Needs to be before createHashcode()
        this.bundle = bundle;
        this.hashCode = createHashCode();
    }

    @Deprecated
    public DefDescriptorImpl(String qualifiedName, Class<T> defClass, DefDescriptor<?> bundle, ContextService contextService) {
        this.bundle = bundle;
        this.defType = DefType.getDefType(defClass);
        if (StringUtils.isBlank(qualifiedName)) {
            throw new AuraRuntimeException("QualifiedName is required for descriptors");
        }

        String prefix = null;
        String namespace = null;
        String name = null;
        String nameParameters = null;

        // Beware, here be dragons!
        // Any change to DefType necessitates a serialVersionUID update 
        switch (defType) {
            case CONTROLLER:
            case TESTSUITE:
            case MODEL:
            case RENDERER:
            case HELPER:
            case STYLE:
            case FLAVORED_STYLE:
            case TYPE:
            case PROVIDER:
            case TOKEN_DESCRIPTOR_PROVIDER:
            case TOKEN_MAP_PROVIDER:
            case INCLUDE:
                Type clazz = TypeParser.parseClass(qualifiedName);
                if (clazz != null) {
                    prefix = clazz.prefix;
                    namespace = clazz.namespace;
                    name = clazz.name;

                    if (clazz.nameParameters != null
                        && defType == org.auraframework.def.DefDescriptor.DefType.TYPE) {

                        nameParameters = clazz.nameParameters;
                    }
                } else {
                    throw new AuraRuntimeException(String.format("Invalid Descriptor Format: %s[%s]", qualifiedName, defType.toString()));
                }

                break;
            // subtypes
            case ACTION:
                throw new AuraRuntimeException(
                        String.format("%s descriptor must be a subdef: %s", defType.name(), qualifiedName));
            case ATTRIBUTE:
            case REGISTEREVENT:
            case METHOD:
            case REQUIRED_VERSION:
            case TESTCASE:
            case TOKEN:
            case TOKENS_IMPORT:
            case ATTRIBUTE_DESIGN:
            case DESIGN_TEMPLATE:
            case DESIGN_TEMPLATE_REGION:
            case INCLUDE_REF:
            case FLAVOR_INCLUDE:
            case FLAVOR_DEFAULT:
                name = qualifiedName;
                break;
            case APPLICATION:
            case COMPONENT:
            case INTERFACE:
            case EVENT:
            case LIBRARY:
            case DOCUMENTATION:
            case TOKENS:
            case DESIGN:
            case SVG:
            case MODULE:
            case FLAVOR_BUNDLE:
            case FLAVORS:
                Type tag = TypeParser.parseTag(qualifiedName);
                if (tag != null) {
                    // default the prefix to 'markup'
                    prefix = tag.prefix != null ? tag.prefix : MARKUP_PREFIX;
                    namespace = tag.namespace;
                    name = tag.name;
                    qualifiedName = buildQualifiedName(prefix, namespace, name);
                } else {
                    throw new AuraRuntimeException(String.format("Invalid Descriptor Format: %s[%s]", qualifiedName, defType.toString()));
                }

                break;
        }

        if (StringUtils.isBlank(prefix)) {
            prefix = contextService.getCurrentContext().getDefaultPrefix(defType);
            if (prefix != null) {
                qualifiedName = buildQualifiedName(prefix, namespace, name);
            }
        }
        this.qualifiedName = qualifiedName;
        this.descriptorName = buildDescriptorName(prefix, namespace, name);
        this.prefix = prefix;
        this.namespace = namespace;
        this.name = name;
        this.hashCode = createHashCode();
        this.nameParameters = nameParameters;
    }

    protected DefDescriptorImpl(String qualifiedName, Class<T> defClass, ContextService contextService) {
        this(qualifiedName, defClass, null, contextService);
    }

    public static String buildQualifiedName(String prefix, String namespace, String name) {
        if (prefix == null && namespace == null) {
            return name;
        }
        if (namespace == null) {
            return prefix + "://" + name;
        }
        if (MARKUP_PREFIX.equals(prefix)) {
            return prefix + "://" + namespace + ":" + name;
        } else {
            return prefix + "://" + namespace + "." + name;
        }

    }

    private static String buildDescriptorName(String prefix, String namespace, String name) {
        if (namespace == null) {
            return name;
        }
        if (MARKUP_PREFIX.equals(prefix)) {
            return namespace + ":" + name;
        } else {
            return namespace + "." + name;
        }
    }

    /**
     * Helper method for various {@link DefDescriptor} subclasses to implement {@link #compareTo(DefDescriptor)}, since
     * interfaces aren't allowed to have static methods, and since {@code DefDescriptor} is an interface rather than an
     * abstract class.
     */
    public static int compare(DefDescriptor<?> dd1, DefDescriptor<?> dd2) {
        if (dd1 == dd2) {
            return 0;
        }

        if (dd1 == null) {
            return -1;
        }

        if (dd2 == null) {
            return 1;
        }

        int value;

        value = dd1.getQualifiedName().compareToIgnoreCase(dd2.getQualifiedName());
        if (value != 0) {
            return value;
        }

        value = dd1.getDefType().compareTo(dd2.getDefType());
        if (value != 0) {
            return value;
        }

        value = compare(dd1.getBundle(), dd2.getBundle());
        return value;
    }

    private int createHashCode() {
        return (bundle == null ? 0 : bundle.hashCode())
                + AuraUtil.hashCodeLowerCase(name, namespace, prefix, defType.ordinal());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    @Override
    public String getDescriptorName() {
        return descriptorName;
    }

    @Override
    public DefType getDefType() {
        return this.defType;
    }

    @Override
    public String getNameParameters() {
        return nameParameters;
    }

    @Override
    public void serialize(Json json) throws IOException {
        json.writeValue(qualifiedName);
    }

    @Override
    public String toString() {
        return qualifiedName;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DefDescriptor) {
            return compare(this, (DefDescriptor<?>)o) == 0;
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    /**
     * @return Returns the prefix.
     */
    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public DefDescriptor<?> getBundle() {
        return this.bundle;
    }

    /**
     * @return Returns isParameterized.
     */
    @Override
    public boolean isParameterized() {
        return nameParameters != null;
    }

    /**
     * @see DefDescriptor#getDef()
     * THIS METHOD IS DEPRECATED. USE Use definitionService.getDefinition(descriptor); on the consumption side.
     */
    @Override
    @Deprecated
    public T getDef() throws QuickFixException {
        return Aura.getDefinitionService().getDefinition(this);
    }

    public static <E extends Definition> DefDescriptor<E> getAssociateDescriptor(DefDescriptor<?> desc,
            Class<E> defClass, String newPrefix) {
        if (desc == null) {
            throw new AuraRuntimeException("descriptor is null");
        }
        return new DefDescriptorImpl<>(desc, defClass, newPrefix);
    }

    /**
     * @see DefDescriptor#exists()
     */
    @Override
    public boolean exists() {
        return Aura.getDefinitionService().exists(this);
    }

    /**
     * Compares one {@link DefDescriptor} to another. Sorting uses (only) the qualified name, case insensitively. Per
     * {@link Comparable}'s spec, throws {@link ClassCastException} if {@code arg} is not a {@code DefDescriptor}.
     */
    @Override
    public int compareTo(DefDescriptor<?> other) {
        return compare(this, other);
    }

}
