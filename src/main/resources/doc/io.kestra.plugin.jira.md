# How to use the Jira plugin

Create issues, add comments, and update fields in Jira from Kestra flows.

## Authentication

Set `baseUrl` to your Jira instance URL (e.g. `https://your-domain.atlassian.net`) on each task. For API token auth, set `username` (your email) and `password` (your Atlassian API token). For OAuth 2.0, set `accessToken` — when present it takes precedence over `username`/`password`. Store credentials in [secrets](https://kestra.io/docs/concepts/secret) and set them on each task.

## Tasks

`issues.Create` creates a new Jira issue — set `projectKey`, `summary`, and optionally `description`, `labels`, and `issueTypeId`. Outputs `id`, `key`, `url` (the issue's browse URL), and `self`; chain `{{ outputs.<taskId>.key }}` into a following `issues.CreateComment` or `issues.UpdateFields` task.

`issues.CreateComment` adds a comment to an existing issue — set `issueIdOrKey` to the issue key or ID and `body` to the comment text. Outputs `id` (comment id), `issueIdOrKey`, `url` (the issue's browse URL with the comment focused), and `self`.

`issues.UpdateFields` updates one or more fields on an existing issue via `PUT /rest/api/2/issue/{issueIdOrKey}` — set `issueIdOrKey` and pass a `fields` map of field names to new values. Outputs `issueIdOrKey` and `url` (the issue's browse URL); Jira's edit-issue endpoint returns no body, so these outputs are derived from the task's own inputs.
