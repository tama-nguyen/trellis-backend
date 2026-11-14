package io.trellis.platform.aspect.feature;

import io.trellis.common.aop.RequiresFeature;
import java.lang.reflect.Method;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/** Fails startup when a {@link RequiresFeature} names an undeclared flag or a missing fallback. */
public final class FeatureFlagUsageVerifier implements SmartInitializingSingleton {
    private final ListableBeanFactory beans;
    private final FeatureFlagSource flags;

    public FeatureFlagUsageVerifier(ListableBeanFactory beans, FeatureFlagSource flags) {
        this.beans = beans;
        this.flags = flags;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name, false);
            if (type != null) {
                Class<?> userClass = ClassUtils.getUserClass(type);
                ReflectionUtils.doWithMethods(
                        userClass,
                        method -> verify(userClass, method),
                        method -> AnnotatedElementUtils.hasAnnotation(method, RequiresFeature.class));
            }
        }
    }

    private void verify(Class<?> type, Method method) {
        RequiresFeature toggle = AnnotatedElementUtils.findMergedAnnotation(method, RequiresFeature.class);
        if (!flags.flagNames().contains(toggle.flag())) {
            throw new IllegalStateException(
                    "@RequiresFeature on " + method + " names undeclared flag '" + toggle.flag() + "'");
        }
        if (!toggle.fallback().isEmpty()
                && ReflectionUtils.findMethod(type, toggle.fallback(), method.getParameterTypes()) == null) {
            throw new IllegalStateException(
                    "@RequiresFeature fallback '" + toggle.fallback() + "' not found for " + method);
        }
    }
}
