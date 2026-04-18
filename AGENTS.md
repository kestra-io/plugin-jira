# Kestra Jira Plugin

## What

- Provides plugin components under `io.kestra.plugin.jira.issues`.
- Includes classes such as `JiraUtil`, `Create`, `JiraClient`, `UpdateFields`.

## Why

- This plugin integrates Kestra with Atlassian Jira.
- It provides tasks that create, update, and search Jira issues.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `jira`

### Key Plugin Classes

- `io.kestra.plugin.jira.issues.Create`
- `io.kestra.plugin.jira.issues.CreateComment`
- `io.kestra.plugin.jira.issues.UpdateFields`

### Project Structure

```
plugin-jira/
├── src/main/java/io/kestra/plugin/jira/issues/
├── src/test/java/io/kestra/plugin/jira/issues/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
