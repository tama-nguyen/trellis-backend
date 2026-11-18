package io.trellis.app.setup;

import io.trellis.platform.events.EventHeaders;
import io.trellis.platform.events.outbox.TopicResolver;
import java.time.Clock;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import lombok.val;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** Runs the setup steps after Liquibase, then exits the JVM with 0 or 1. */
@Component
@ConditionalOnProperty(prefix = "trellis.setup", name = "enabled", havingValue = "true")
class SetupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SetupRunner.class);
    private static final long ADMIN_TIMEOUT_SECONDS = 30;

    private final SetupProperties properties;
    private final S3Properties s3;
    private final KafkaProperties kafka;
    private final TopicResolver topics;
    private final ConfigurableApplicationContext context;
    private final Clock clock;

    SetupRunner(
            final SetupProperties properties,
            final S3Properties s3,
            final KafkaProperties kafka,
            final TopicResolver topics,
            final ConfigurableApplicationContext context,
            final Clock clock) {
        this.properties = properties;
        this.s3 = s3;
        this.kafka = kafka;
        this.topics = topics;
        this.context = context;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments args) {
        val exitCode = performSetup();
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    private int performSetup() {
        try {
            log.info("Setup: database schema is up to date (Liquibase ran at startup)");
            if (properties.createKafkaTopics()) {
                createTopics();
            }
            if (properties.createS3Bucket()) {
                val created = new S3BucketCreator(clock, properties.s3()).createBucket(s3);
                log.atInfo()
                        .addKeyValue("bucket", s3.bucket())
                        .addKeyValue("created", created)
                        .log("Setup: object storage bucket ready");
            }
            log.info("Setup finished");
            return 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.atError().setCause(e).log("Setup failed");
            return 1;
        }
    }

    private void createTopics() throws Exception {
        val wanted = new ArrayList<NewTopic>();
        for (final String eventType : properties.topics()) {
            val topic = topics.topicFor(eventType);
            wanted.add(new NewTopic(topic, properties.partitions(), properties.replicationFactor()));
            wanted.add(new NewTopic(
                    topic + EventHeaders.DLQ_SUFFIX, properties.partitions(), properties.replicationFactor()));
        }
        val config = kafka.buildAdminProperties();
        try (val admin = Admin.create(config)) {
            val existing = admin.listTopics().names().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            val missing =
                    wanted.stream().filter(t -> !existing.contains(t.name())).toList();
            if (!missing.isEmpty()) {
                admin.createTopics(missing).all().get(ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            log.atInfo()
                    .addKeyValue("created", missing.stream().map(NewTopic::name).toList())
                    .addKeyValue("wanted", wanted.size())
                    .log("Setup: Kafka topics ready");
        }
    }
}
