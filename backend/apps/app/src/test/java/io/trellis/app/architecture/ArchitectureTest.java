package io.trellis.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.trellis.common.annotation.Audited;
import io.trellis.common.annotation.DistributedLock;
import io.trellis.common.annotation.Idempotent;
import io.trellis.common.annotation.LogExecution;
import io.trellis.common.annotation.RateLimited;
import io.trellis.common.annotation.RequiresFeature;
import io.trellis.common.annotation.RequiresPermission;
import io.trellis.common.arch.ArchExempt;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Architecture rules of the backend (production classes of every module). Each rule has an id; an intentional
 * exception is marked {@code @ArchExempt(rule = "<id>", reason = "...")} on the class or method.
 */
class ArchitectureTest {
    private static final List<Class<? extends Annotation>> AOP_ANNOTATIONS = List.of(
            Audited.class,
            RequiresPermission.class,
            Idempotent.class,
            RateLimited.class,
            DistributedLock.class,
            LogExecution.class,
            RequiresFeature.class);

    /** {@code io.trellis.<module>} where the module is not part of the platform. */
    private static final String BUSINESS_MODULE_PACKAGE =
            "^io\\.trellis\\.(?!(?:app|common|platform)\\.)[a-z][a-z0-9]*";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.trellis");
    }

    /** Packages of the modules dissolved by decisions T18 and T19; nothing may recreate them. */
    private static final List<String> DISSOLVED_MODULE_PACKAGES =
            List.of("io.trellis.aop..", "io.trellis.platform.graphql..");

    /** Gradle project names of the modules dissolved by decisions T18 and T19. */
    private static final List<String> DISSOLVED_MODULES = List.of(
            "trellis-aop",
            "trellis-platform-graphql",
            "trellis-platform-security",
            "trellis-platform-persistence",
            "trellis-platform-observability");

    @Test
    void commonHasNoFrameworkDependencies() {
        noClasses()
                .that()
                .resideInAPackage("io.trellis.common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "tools.jackson..",
                        "com.fasterxml..",
                        "jakarta..",
                        "io.trellis.platform..",
                        "io.trellis.app..")
                .because("trellis-common must stay usable without any framework (decisions T19, T18)")
                .check(classes);
    }

    /**
     * Decision T18 (b): the base platform must stay shippable without the HTTP edge, so a consumer or upgrade
     * job can run it. A single reference from the base to a web class also makes the module split circular.
     */
    @Test
    void platformBaseDoesNotDependOnTheWebTier() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "io.trellis.platform.persistence..",
                        "io.trellis.platform.security..",
                        "io.trellis.platform.observability..",
                        "io.trellis.platform.aspect..",
                        "io.trellis.platform.events..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.trellis.platform.web..",
                        "org.springframework.web..",
                        "org.springframework.graphql..",
                        "org.springframework.security.web..",
                        "graphql..",
                        "jakarta.servlet..")
                .because("trellis-platform ships in jobs that have no servlet container (decision T18 (b))")
                .check(classes);
    }

    /** The modules merged away by T18/T19 must not reappear, neither as packages nor as Gradle projects. */
    @Test
    void dissolvedModulesDoNotComeBack() {
        noClasses()
                .should()
                .resideInAnyPackage(DISSOLVED_MODULE_PACKAGES.toArray(String[]::new))
                .because("T18/T19 merged these modules away; their content lives in common, platform and"
                        + " platform-web")
                .check(classes);

        String settings = readRepoFile("settings.gradle.kts");
        for (String module : DISSOLVED_MODULES) {
            assertFalse(
                    settings.contains('"' + module + '"'),
                    "settings.gradle.kts still declares the dissolved module " + module);
            assertFalse(moduleDirectoryExists(module), module + "/ came back under backend/; T18/T19 merged it away");
        }
    }

    /** Any directory of that name anywhere under {@code backend/}, whatever the layout of the day. */
    private static boolean moduleDirectoryExists(String module) {
        try (Stream<Path> tree = Files.walk(repoRoot().resolve("backend"), 4)) {
            return tree.filter(Files::isDirectory)
                    .anyMatch(path -> path.getFileName().toString().equals(module));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk backend/", e);
        }
    }

    private static Path repoRoot() {
        // trellis.contracts.dir is <repo>/contracts, set for every test task by the java-library convention.
        return Path.of(System.getProperty("trellis.contracts.dir")).getParent();
    }

    private static String readRepoFile(String relative) {
        try {
            return Files.readString(repoRoot().resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + relative, e);
        }
    }

    @Test
    void platformModulesDoNotDependOnTheApp() {
        noClasses()
                .that()
                .resideInAnyPackage("io.trellis.common..", "io.trellis.platform..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.trellis.app..")
                .check(classes);
    }

    @Test
    void aopAnnotationsOnlyOnApplicationServices() {
        methods()
                .that(areAnnotatedWithAnyAopAnnotation())
                .and(notExempt("aop-application-layer"))
                .should()
                .beDeclaredInClassesThat()
                .resideInAPackage("..application..")
                .because(
                        "aspects guard use cases; controllers, repositories and domain objects bypass or duplicate them")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aopAnnotatedMethodsAreProxyable() {
        methods()
                .that(areAnnotatedWithAnyAopAnnotation())
                .should()
                .bePublic()
                .andShould()
                .notHaveModifier(JavaModifier.FINAL)
                .andShould()
                .notHaveModifier(JavaModifier.STATIC)
                .andShould(beDeclaredInNonFinalSpringBean())
                .because("Spring AOP proxies only intercept public, overridable methods of beans")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aopAnnotatedMethodsAreNeverSelfInvoked() {
        methods()
                .that(areAnnotatedWithAnyAopAnnotation())
                .should(notBeCalledFromTheirOwnClass())
                .because("a call through `this` skips the proxy, so the aspect silently does not run")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void auditedMethodsAreTransactional() {
        methods()
                .that()
                .areAnnotatedWith(Audited.class)
                .should()
                .beAnnotatedWith(Transactional.class)
                .because("@Audited writes inside the business transaction")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void noValueAnnotation() {
        ArchRule fields = noClasses()
                .that(notExemptClass("no-value-annotation"))
                .should()
                .dependOnClassesThat()
                .areAssignableTo(Value.class)
                .because("configuration is bound through @ConfigurationProperties records, one source of truth");
        fields.check(classes);
    }

    @Test
    void configurationPropertiesAreRecords() {
        classes()
                .that()
                .areAnnotatedWith(ConfigurationProperties.class)
                .should()
                .beRecords()
                .check(classes);
    }

    @Test
    void noCommonPoolAsync() {
        noClasses()
                .should()
                .callMethod(CompletableFuture.class, "runAsync", Runnable.class)
                .orShould()
                .callMethod(CompletableFuture.class, "supplyAsync", Supplier.class)
                .because("the common pool drops MDC, trace and RequestContext; pass the applicationTaskExecutor")
                .check(classes);
    }

    @Test
    void noDirectClockReads() {
        noClasses()
                .that(notExemptClass("no-direct-clock"))
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .orShould()
                .callMethod(LocalDate.class, "now")
                .orShould()
                .callMethod(OffsetDateTime.class, "now")
                .orShould()
                .callMethod(ZonedDateTime.class, "now")
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .because("time comes from the injected java.time.Clock so tests and jobs can control it")
                .check(classes);
    }

    @Test
    void noJpa() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                .because("SQL is native over JdbcClient (decision T10)")
                .check(classes);
    }

    /*
     * Business modules (io.trellis.<module>.{api,domain,application,infrastructure,web}, the Notice sample first):
     * web -> application -> domain, infrastructure implements application ports, nobody depends on web. Platform
     * modules, aop, common and the app are not business modules and are not matched.
     */

    @Test
    void businessDomainIsFrameworkFree() {
        noClasses()
                .that(inBusinessLayer("domain"))
                .should()
                .dependOnClassesThat(inBusinessLayer("application")
                        .or(inBusinessLayer("infrastructure"))
                        .or(inBusinessLayer("web")))
                .orShould()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "tools.jackson..", "java.sql..")
                .because("the domain holds rules only: no Spring, no SQL, no knowledge of use cases or adapters")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void businessApplicationDoesNotDependOnAdapters() {
        noClasses()
                .that(inBusinessLayer("application"))
                .should()
                .dependOnClassesThat(inBusinessLayer("infrastructure").or(inBusinessLayer("web")))
                .because("use cases declare ports; infrastructure implements them and web calls them")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void businessWebStaysThin() {
        noClasses()
                .that(inBusinessLayer("web"))
                .should()
                .dependOnClassesThat(inBusinessLayer("infrastructure"))
                .orShould()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.jdbc..", "io.trellis.platform.events.outbox..")
                .because("REST and GraphQL controllers (resolvers included) go through application services, which"
                        + " own authorization and transactions; a resolver reading a repository bypasses both")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void nothingDependsOnBusinessWeb() {
        noClasses()
                .that(DescribedPredicate.not(inBusinessLayer("web")))
                .should()
                .dependOnClassesThat(inBusinessLayer("web"))
                .because("controllers are entry points; logic other modules need belongs in application")
                .allowEmptyShould(true)
                .check(classes);
    }

    private static DescribedPredicate<JavaClass> inBusinessLayer(String layer) {
        Pattern pattern = Pattern.compile(BUSINESS_MODULE_PACKAGE + "\\." + layer + "(\\..*)?$");
        return DescribedPredicate.describe(
                "are in the " + layer + " layer of a business module",
                type -> pattern.matcher(type.getPackageName()).matches());
    }

    private static DescribedPredicate<JavaMethod> areAnnotatedWithAnyAopAnnotation() {
        return DescribedPredicate.describe(
                "are annotated with a platform AOP annotation",
                method -> AOP_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith));
    }

    private static DescribedPredicate<JavaMethod> notExempt(String rule) {
        return DescribedPredicate.describe(
                "are not exempt from " + rule,
                method -> !isExempt(
                                method.tryGetAnnotationOfType(ArchExempt.class).orElse(null), rule)
                        && !isExempt(
                                method.getOwner()
                                        .tryGetAnnotationOfType(ArchExempt.class)
                                        .orElse(null),
                                rule));
    }

    private static DescribedPredicate<JavaClass> notExemptClass(String rule) {
        return DescribedPredicate.describe(
                "are not exempt from " + rule,
                type -> !isExempt(type.tryGetAnnotationOfType(ArchExempt.class).orElse(null), rule));
    }

    private static boolean isExempt(ArchExempt exempt, String rule) {
        return exempt != null && exempt.rule().equals(rule);
    }

    private static ArchCondition<JavaMethod> beDeclaredInNonFinalSpringBean() {
        return new ArchCondition<>("be declared in a non-final Spring bean (@Component or a stereotype)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass owner = method.getOwner();
                boolean bean = owner.isMetaAnnotatedWith(Component.class) || owner.isAnnotatedWith(Component.class);
                boolean ok = bean && !owner.getModifiers().contains(JavaModifier.FINAL);
                events.add(
                        new SimpleConditionEvent(method, ok, method.getFullName() + " owner is not a non-final bean"));
            }
        };
    }

    private static ArchCondition<JavaMethod> notBeCalledFromTheirOwnClass() {
        return new ArchCondition<>("not be called from their own class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getCallsOfSelf()) {
                    if (call.getOriginOwner().equals(method.getOwner())) {
                        events.add(SimpleConditionEvent.violated(
                                method, call.getDescription() + " bypasses the proxy (self-invocation)"));
                    }
                }
            }
        };
    }
}
