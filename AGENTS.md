# Kestra Jira Plugin

## What

- Provides plugin components under `io.kestra.plugin.jira.issues` and `io.kestra.plugin.jira.comments`.
- Includes classes such as `JiraUtil`, `Create`, `JiraClient`, `UpdateFields`, and `Get` tasks that fetch issues (by key or via JQL) and comments.

## Why

- What user problem does this solve? Teams need to create, update, and search Jira issues from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Atlassian Jira steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Atlassian Jira.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `jira`

### Key Plugin Classes

- `io.kestra.plugin.jira.issues.Create`
- `io.kestra.plugin.jira.issues.CreateComment`
- `io.kestra.plugin.jira.issues.Get`
- `io.kestra.plugin.jira.issues.UpdateFields`
- `io.kestra.plugin.jira.comments.Get`

### Project Structure

```
plugin-jira/
├── src/main/java/io/kestra/plugin/jira/issues/
├── src/main/java/io/kestra/plugin/jira/comments/
├── src/test/java/io/kestra/plugin/jira/issues/
├── src/test/java/io/kestra/plugin/jira/comments/
├── src/main/resources/metadata/
├── src/main/resources/doc/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
