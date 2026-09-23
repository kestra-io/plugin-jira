package io.kestra.plugin.jira.issues;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractJiraTest {

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected JiraMockController mockController;

    protected EmbeddedServer embeddedServer;

    @BeforeEach
    void setUp() {
        if (embeddedServer == null || !embeddedServer.isRunning()) {
            embeddedServer = applicationContext.getBean(EmbeddedServer.class);
            embeddedServer.start();
        }
        mockController.requests.clear();
    }

    @AfterAll
    void tearDown() {
        if (embeddedServer != null && embeddedServer.isRunning()) {
            embeddedServer.stop();
        }
    }

    protected String getApiBaseUrl() {
        return embeddedServer.getURI().toString();
    }
}
