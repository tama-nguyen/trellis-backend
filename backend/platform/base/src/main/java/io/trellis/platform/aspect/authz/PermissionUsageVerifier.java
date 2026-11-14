package io.trellis.platform.aspect.authz;

import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.security.KnownPermissions;
import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRequirement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Fails startup when an {@link RequiresPermission} is empty or names a permission that no {@code
 * PermissionCatalog} declares. A typo would otherwise deny everyone forever, silently.
 */
public final class PermissionUsageVerifier implements SmartInitializingSingleton {
    private final ListableBeanFactory beans;
    private final KnownPermissions known;

    public PermissionUsageVerifier(ListableBeanFactory beans, KnownPermissions known) {
        this.beans = beans;
        this.known = known;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name, false);
            if (type == null) {
                continue;
            }
            ReflectionUtils.doWithMethods(ClassUtils.getUserClass(type), this::verify, this::isAnnotated);
        }
    }

    private boolean isAnnotated(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, RequiresPermission.class);
    }

    private void verify(Method method) {
        RequiresPermission annotation = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
        try {
            PermissionRequirement.of(annotation.anyOf(), annotation.allOf());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid @RequiresPermission on " + method + ": " + e.getMessage(), e);
        }
        Stream.concat(Arrays.stream(annotation.anyOf()), Arrays.stream(annotation.allOf()))
                .map(Permission::of)
                .filter(p -> !known.isDeclared(p))
                .findFirst()
                .ifPresent(p -> {
                    throw new IllegalStateException(
                            "@RequiresPermission on " + method + " names undeclared permission '" + p + "'");
                });
    }
}
