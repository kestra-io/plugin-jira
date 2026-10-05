# How to use the Jira plugin

Create issues, add comments, and update fields in Jira from Kestra flows.

## Authentication

Set `baseUrl` to your Jira instance URL (e.g. `https://your-domain.atlassian.net`) on each task. For API token auth, set `username` (your email) and `password` (your Atlassian API token). For OAuth 2.0, set `accessToken` — when present it takes precedence over `username`/`password`. Store credentials in [secrets](https://kestra.io/docs/concepts/secret) and set them on each task.

## Tasks

`issues.Create` creates a new Jira issue — set `projectKey`, `summary`, and optionally `description`, `labels`, and `issueTypeId`. Outputs `id`, `key`, `url` (the issue's browse URL), and `self`; chain `{{ outputs.<taskId>.key }}` into a following `issues.CreateComment` or `issues.UpdateFields` task.

`issues.CreateComment` adds a comment to an existing issue — set `issueIdOrKey` to the issue key or ID and `body` to the comment text. Outputs `id` (comment id), `issueIdOrKey`, `url` (the issue's browse URL with the comment focused), and `self`.

`issues.UpdateFields` updates one or more fields on an existing issue via `PUT /rest/api/2/issue/{issueIdOrKey}` — set `issueIdOrKey` and pass a `fields` map of field names to new values. Outputs `issueIdOrKey` and `url` (the issue's browse URL); Jira's edit-issue endpoint returns no body, so these outputs are derived from the task's own inputs.

`issues.Get` fetches existing issues. With `fetchType: FETCH_ONE`, set `issueIdOrKey` to an issue key or id and the issue payload is exposed as `row`. With `fetchType: FETCH` or `STORE`, set `jql` to a [JQL query](https://support.atlassian.com/jira-software-cloud/docs/use-advanced-search-with-jql-query-language/) and all matching issues are exposed as `rows` (or stored in Kestra storage as `uri`). Results are paged automatically, so `size` reflects the full match count. On Jira Cloud the search uses `POST /rest/api/3/search/jql` (the classic `/rest/api/2/search` endpoint was removed from Jira Cloud); on Jira Server or Data Center, set `searchApi: SERVER` to use `GET /rest/api/2/search`. Because the Cloud endpoint returns only issue ids and keys unless fields are requested, the task requests the `fields` you list (by default `summary`, `status`, `description`, `labels`, `created`, and `updated`); `maxResults` is the page size (1 to 5000), not a cap on the total.

`comments.Get` (in the `io.kestra.plugin.jira.comments` sub-group) fetches comments of an existing issue. With `fetchType: FETCH` or `STORE`, set `issueIdOrKey` and all comments of that issue are exposed as `rows` (or stored in Kestra storage as `uri`). With `fetchType: FETCH_ONE`, set `commentId` to fetch a single comment as `row`. Results are paged automatically, so `size` reflects the full comment count; `maxResults` is the page size (1 to 5000).

`projectKey`, `summary`, `description`, `labels`, and `issueTypeId` are deprecated and ignored on `issues.CreateComment` and `issues.UpdateFields`; they are kept only for backward compatibility.
