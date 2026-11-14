package io.trellis.platform.security.authz;

import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionCatalog;
import io.trellis.common.security.PermissionRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/** Builds {@link PermissionRegistry} from every {@link PermissionCatalog} class under the given packages. */
public final class PermissionCatalogScanner {
    private PermissionCatalogScanner() {}

    public static PermissionRegistry scan(List<String> basePackages, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(PermissionCatalog.class));
        Set<Permission> permissions = new HashSet<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> catalog = ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader);
                permissions.addAll(constantsOf(catalog));
            }
        }
        Set<Permission> frozen = Set.copyOf(permissions);
        return () -> frozen;
    }

    private static Set<Permission> constantsOf(Class<?> catalog) {
        Set<Permission> found = new HashSet<>();
        for (Field field : catalog.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod)
                    && Modifier.isStatic(mod)
                    && Modifier.isFinal(mod)
                    && field.getType() == String.class) {
                try {
                    found.add(Permission.of((String) field.get(null)));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read permission constant " + field, e);
                }
            }
        }
        return found;
    }
}
