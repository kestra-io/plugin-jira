# Kestra Jira Plugin

## What

- Provides plugin components under `io.kestra.plugin.jira.issues`.
- Includes classes such as `JiraUtil`, `Create`, `JiraClient`, `UpdateFields`.

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
