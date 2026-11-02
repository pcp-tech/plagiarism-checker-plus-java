# Plagiarism Checker Plus for Java

Official client for the [Plagiarism Checker Plus API](https://plagiarismcheckerplus.com/integrations/api). Java 11+, uses `java.net.http`, no runtime dependencies.

The API needs a paid plan. A key on the free plan returns `403 plan_required`.

```xml
<dependency>
  <groupId>com.plagiarismcheckerplus</groupId>
  <artifactId>sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
import com.plagiarismcheckerplus.sdk.ApiException;
import com.plagiarismcheckerplus.sdk.PlagiarismCheckerPlus;
import java.util.List;
import java.util.Map;

PlagiarismCheckerPlus client = new PlagiarismCheckerPlus(System.getenv("PCP_API_KEY"));

try {
    Map<String, Object> created = client.createScan(essayText, List.of("plagiarism", "ai_detect"), "essay-draft-1");
    Map<String, Object> scan = client.waitForScan((String) created.get("id"));

    @SuppressWarnings("unchecked")
    Map<String, Object> results = (Map<String, Object>) scan.get("results");
    System.out.println(results);
    // aiDetection.score is a likelihood, not proof that the text was written by AI.
} catch (ApiException e) {
    if (ApiException.TEXT_TOO_SHORT.equals(e.getErrorCode())) {
        System.out.println("Send at least 30 words for plagiarism, 50 for AI detection.");
    } else {
        throw e;
    }
}
```

Responses are `Map<String, Object>` values that mirror the API's JSON, so new fields reach you without an SDK update.

## Methods

| Method | API call |
|---|---|
| `createScan(text, tools, name)` | `POST /scans` |
| `getScan(id, includeText)` | `GET /scans/{id}` |
| `listScans(page, limit)` | `GET /scans` |
| `getAccount()` | `GET /account` |
| `waitForScan(id)` or `waitForScan(id, timeout, interval, maxInterval, includeText)` | Polls until `completed` or `failed` |

`waitForScan` starts at 2 seconds, grows by 1.5× up to 10 seconds, waits for `Retry-After` on `429`, and throws `ScanTimeoutException` after 300 seconds by default.

Failed calls throw `ApiException` with `getStatus()`, `getErrorCode()` (such as `plan_required`, `text_too_short`, `doc_too_long`, `insufficient_words`) and `getRetryAfterSeconds()`.

Run the tests with `mvn test`.

## Error handling

Failed calls raise `com.plagiarismcheckerplus.sdk.ApiException`. It carries the HTTP status and the API error code:

| Code | Status | Meaning |
|---|---|---|
| `plan_required` | 403 | The key's plan has no API access. The API needs a paid plan. |
| `text_too_short` | 400 | Plagiarism needs at least 30 words, AI detection at least 50. Nothing is charged. |
| `doc_too_long` | 400 | The text is longer than the plan allows per check. |
| `insufficient_words` | 402 | Not enough words left on the account. |

Other codes (for example on `401`, `404`, `429` or `503`) come through as the raw string the API sends. On `429` the client honours `Retry-After`. `waitForScan` raises `com.plagiarismcheckerplus.sdk.ScanTimeoutException` when it runs out of time. A scan whose status is `failed` is returned, not raised, so check `status`.

## Notes

- **An AI score is a likelihood, not proof** that someone used AI. A similarity score does not decide on its own whether text is plagiarism. Review the result yourself, and do not use either score alone to accuse anyone.
- **The API needs a paid plan.** A key on the free plan returns `403 plan_required`.
- Each tool you run is billed at the full word count. The rate limit is 60 requests a minute per key.
- API documentation: https://plagiarismcheckerplus.com/integrations/api
- The OpenAPI spec this client follows is in [`spec/openapi.json`](spec/openapi.json).

## Other languages

| Language | Repository | Install |
|---|---|---|
| JavaScript / TypeScript | [plagiarism-checker-plus-js](https://github.com/pcp-tech/plagiarism-checker-plus-js) | `npm install plagiarism-checker-plus` |
| Python | [plagiarism-checker-plus-python](https://github.com/pcp-tech/plagiarism-checker-plus-python) | `pip install plagiarism-checker-plus` |
| PHP | [plagiarism-checker-plus-php](https://github.com/pcp-tech/plagiarism-checker-plus-php) | `composer require plagiarismcheckerplus/sdk` |
| Ruby | [plagiarism-checker-plus-ruby](https://github.com/pcp-tech/plagiarism-checker-plus-ruby) | `gem install plagiarism_checker_plus` |
| Go | [plagiarism-checker-plus-go](https://github.com/pcp-tech/plagiarism-checker-plus-go) | `go get github.com/pcp-tech/plagiarism-checker-plus-go` |
| **Java** (this repository) | [plagiarism-checker-plus-java](https://github.com/pcp-tech/plagiarism-checker-plus-java) | Maven `com.plagiarismcheckerplus:sdk` |
| C# / .NET | [plagiarism-checker-plus-dotnet](https://github.com/pcp-tech/plagiarism-checker-plus-dotnet) | `dotnet add package PlagiarismCheckerPlus` |

## License

MIT. Copyright (c) 2026 Plagiarism Checker Plus.
