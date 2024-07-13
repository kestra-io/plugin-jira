package io.kestra.plugin.jira.issues;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
@KestraTest
public class JiraClientTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "description", "A description of a ticket"
        ));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        JiraClient task = JiraClient.builder()
            .baseUrl(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Files.asCharSource(
                    new File(Objects.requireNonNull(JiraClientTest.class.getClassLoader()
                            .getResource("jira.peb"))
                        .toURI()),
                    Charsets.UTF_8
                ).read()
            )
            .username("user@domain.com")
            .password(UUID.randomUUID().toString())
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("description"));
    }
}
