package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.Task;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

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
    void createCommentAndUpdateFieldsExposeIssueFieldsAsDeprecatedAndOptional() {
        for (Class<? extends Task> taskClass : List.of(CreateComment.class, UpdateFields.class)) {
            var generate = jsonSchemaGenerator.properties(Task.class, taskClass);
            var properties = (Map<String, Map<String, Object>>) generate.get("properties");
            var required = (List<String>) generate.getOrDefault("required", List.of());

            for (String deprecatedField : List.of("projectKey", "summary", "description", "labels", "issueTypeId")) {
                var propertySchema = properties.get(deprecatedField);
                assertThat("'" + deprecatedField + "' must still be exposed (deprecated) on " + taskClass.getSimpleName(), propertySchema, is(notNullValue()));
                assertThat("'" + deprecatedField + "' must be marked deprecated on " + taskClass.getSimpleName(), deprecated(propertySchema), is(true));
                assertThat("'" + deprecatedField + "' must not be required on " + taskClass.getSimpleName(), required, not(hasItem(deprecatedField)));
            }
        }
    }

    // Dynamic-renderable non-String properties (e.g. Integer, enum, List) render as an "anyOf" of type
    // variants (typed value + Pebble expression string) instead of a flat schema, so `$group`/`$secret`/
    // `$deprecated` live on the first "anyOf" entry rather than at the top level.
    private static Object group(Map<String, Object> propertySchema) {
        return metadata(propertySchema).get("$group");
    }

    private static Object secret(Map<String, Object> propertySchema) {
        return metadata(propertySchema).get("$secret");
    }

    private static Object deprecated(Map<String, Object> propertySchema) {
        return metadata(propertySchema).get("$deprecated");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Map<String, Object> propertySchema) {
        if (propertySchema.containsKey("$group") || propertySchema.containsKey("$secret") || propertySchema.containsKey("$deprecated")) {
            return propertySchema;
        }
        var anyOf = (List<Map<String, Object>>) propertySchema.get("anyOf");
        return anyOf.getFirst();
    }
}
