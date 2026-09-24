package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.Task;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class JiraSchemaTest {

    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    @Test
    @SuppressWarnings("unchecked")
    void createPropertiesAreGroupedAndUsernameIsNotSecret() {
        var generate = jsonSchemaGenerator.properties(Task.class, Create.class);
        var properties = (Map<String, Map<String, Object>>) generate.get("properties");

        assertThat(group(properties.get("baseUrl")), is("connection"));
        assertThat(group(properties.get("username")), is("connection"));
        assertThat(group(properties.get("projectKey")), is("destination"));
        assertThat(group(properties.get("summary")), is("main"));
        assertThat(group(properties.get("description")), is("main"));

        assertThat(secret(properties.get("username")), not(is(true)));
        assertThat(secret(properties.get("password")), is(true));
        assertThat(secret(properties.get("accessToken")), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createCommentAndUpdateFieldsDoNotExposeIssueOnlyFields() {
        for (Class<? extends Task> taskClass : List.of(CreateComment.class, UpdateFields.class)) {
            var generate = jsonSchemaGenerator.properties(Task.class, taskClass);
            var properties = (Map<String, Map<String, Object>>) generate.get("properties");

            for (String issueOnlyField : List.of("summary", "description", "labels", "issueTypeId")) {
                assertThat("'" + issueOnlyField + "' must not be exposed on " + taskClass.getSimpleName(), properties.get(issueOnlyField), is(nullValue()));
            }
        }
    }

    // Dynamic-renderable non-String properties (e.g. Integer, enum, List) render as an "anyOf" of type
    // variants (typed value + Pebble expression string) instead of a flat schema, so `$group`/`$secret`
    // live on the first "anyOf" entry rather than at the top level.
    private static Object group(Map<String, Object> propertySchema) {
        return metadata(propertySchema).get("$group");
    }

    private static Object secret(Map<String, Object> propertySchema) {
        return metadata(propertySchema).get("$secret");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> propertySchema) {
        if (propertySchema.containsKey("$group") || propertySchema.containsKey("$secret")) {
            return propertySchema;
        }
        var anyOf = (List<Map<String, Object>>) propertySchema.get("anyOf");
        return anyOf.getFirst();
    }
}
