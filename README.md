# Plagiarism Checker Plus for Java

Official client for the [Plagiarism Checker Plus API](https://plagiarismcheckerplus.com/integrations/api). Java 11+, uses `java.net.http`, no runtime dependencies.

Get a free API key in the [developer portal](https://app.plagiarismcheckerplus.com/developers). Test keys are free and unlimited, and every free account gets 5,000 live API words once, with no card. See [API pricing](https://plagiarismcheckerplus.com/api-pricing).

## Install

```xml
<dependency>
  <groupId>com.plagiarismcheckerplus</groupId>
  <artifactId>sdk</artifactId>
  <version>1.1.0</version>
</dependency>
```

## Quick start

Get a free API key at https://app.plagiarismcheckerplus.com/developers (test keys are free and unlimited).

```java
import com.plagiarismcheckerplus.sdk.ApiException;
import com.plagiarismcheckerplus.sdk.PlagiarismCheckerPlus;
import java.util.List;
import java.util.Map;

PlagiarismCheckerPlus client = new PlagiarismCheckerPlus(System.getenv("PCP_API_KEY"));

try {
    // 50+ words for AI detection.
    Map<String, Object> created = client.createScan(essayText, List.of("plagiarism", "ai_detect"), "essay-draft-1");
    Map<String, Object> scan = client.waitForScan((String) created.get("id"));

    if (Boolean.TRUE.equals(scan.get("sandbox"))) System.out.println("Test key: these are sample results.");
    @SuppressWarnings("unchecked")
    Map<String, Object> results = (Map<String, Object>) scan.get("results");
    System.out.println(results);
    // aiDetection.score is a likelihood, not proof that the text was written by AI.
} catch (ApiException e) {
    Map<String, Object> details = e.getDetails(); // the response's error object, with any detail fields
    if (ApiException.TEXT_TOO_SHORT.equals(e.getErrorCode())) {
        System.out.println("Send at least 30 words for plagiarism, 50 for AI detection.");
    } else if (ApiException.TRIAL_EXHAUSTED.equals(e.getErrorCode())) {
        System.out.println("Free API trial used up. Buy words or choose an API plan: " + details.get("billingUrl"));
    } else if (ApiException.INSUFFICIENT_WORDS.equals(e.getErrorCode())) {
        System.out.println("Need " + details.get("wordsNeeded") + " words, " + details.get("wordsAvailable") + " available. Top up: " + details.get("billingUrl"));
    } else if (ApiException.KEY_CAP_REACHED.equals(e.getErrorCode())) {
        System.out.println("This key reached its monthly cap of " + details.get("monthlyWordCap") + " words.");
    } else {
        throw e;
    }
}
```

Responses are `Map<String, Object>` values that mirror the API's JSON, so new fields (such as `sandbox` on scans and `trial`, `balances`, `autoTopup` and `key` on the account) reach you without an SDK update.

## Test keys, free trial and pricing

- **Test keys** (`pcp_test_...`) are free and unlimited on every plan, including free. They never run a check: `createScan` returns as usual and `getScan` returns `completed` with fixed sample results in the same shape as a real scan, marked `"sandbox": true`. Use them to build and test your integration.
- **Free API trial**: each free account gets 5,000 API words once for live keys (`pcp_...`), with no card. When the trial cannot cover a request, the API returns `402 trial_exhausted` and nothing is charged.
- **Paid app plans** (Student, Personal, Team) use their plan words for API calls.
- **API plans**: Starter $25/month (300,000 words), Growth $99/month (1,500,000 words), Scale $399/month (7,500,000 words).
- **Pay as you go**: words from $4.50 (50,000 words, $0.09 per 1,000 words). Purchased words never expire.

A scan costs its word count once per tool: 800 words checked for plagiarism and AI costs 1,600 words. Prices: [API pricing](https://plagiarismcheckerplus.com/api-pricing). Keys, usage, billing and webhooks: [developer portal](https://app.plagiarismcheckerplus.com/developers). `getAccount()` returns what is left in `trial` and `balances`, plus `autoTopup` and this key's `key.mode` and `key.monthlyWordCap`.

The rate limit is 60 requests a minute per key on paid app plans and for accounts with an API plan or purchased API words, and 10 a minute otherwise (free trial, test keys on the free plan).

## Webhooks

Instead of polling, register an https endpoint in the [developer portal](https://app.plagiarismcheckerplus.com/developers) to receive `scan.completed` and `scan.failed` for checks made with live keys. Each delivery carries

```
PCP-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>
```

`Webhook.verifySignature(secret, header, rawBody)` checks it with the endpoint's signing secret (`whsec_...`). Pass the raw request body, before JSON parsing. It compares in constant time and rejects timestamps more than 300 seconds from now (the tolerance is configurable). The overload `verifySignature(secret, header, bodyBytes, toleranceSeconds, nowSeconds)` sets the tolerance and time yourself.

```java
import com.plagiarismcheckerplus.sdk.Webhook;

// Spring example: take the body as a String (or byte[]) so it is not re-serialised.
@PostMapping("/webhooks/pcp")
public ResponseEntity<String> pcpWebhook(@RequestHeader(Webhook.SIGNATURE_HEADER) String signature, @RequestBody String raw) {
    if (!Webhook.verifySignature(System.getenv("PCP_WEBHOOK_SECRET"), signature, raw)) {
        return ResponseEntity.badRequest().body("Invalid signature");
    }
    // Parse raw with your JSON library; event.type is "scan.completed" or "scan.failed".
    return ResponseEntity.ok("");
}
```

An event can be delivered more than once (retries). Use its `id` to ignore repeats.

## Methods

| Method | API call |
|---|---|
| `createScan(text, tools, name)` | `POST /scans` |
| `getScan(id, includeText)` | `GET /scans/{id}` |
| `listScans(page, limit)` | `GET /scans` |
| `getAccount()` | `GET /account` |
| `waitForScan(id)` or `waitForScan(id, timeout, interval, maxInterval, includeText)` | Polls until `completed` or `failed` |
| `Webhook.verifySignature(secret, header, body)` | Checks a webhook's `PCP-Signature` (no API call) |

`waitForScan` starts at 2 seconds, grows by 1.5x up to 10 seconds, waits for `Retry-After` on `429`, and throws `ScanTimeoutException` after 300 seconds by default.

Failed calls throw `ApiException` with `getStatus()`, `getErrorCode()` (such as `trial_exhausted`, `insufficient_words`, `text_too_short`), `getRetryAfterSeconds()`, `getBody()` and `getDetails()` (the response's error object).

Run the tests with `mvn test`.

## Error codes

Failed calls raise `com.plagiarismcheckerplus.sdk.ApiException`. It carries the HTTP status and the API error code (constants on `ApiException`):

| Code | Status | Meaning |
|---|---|---|
| `invalid_request`, `invalid_json` | 400 | The request body or parameters are not valid. |
| `text_too_short` | 400 | Plagiarism needs at least 30 words, AI detection at least 50. Nothing is charged. |
| `doc_too_long` | 400 | The text is longer than the account allows per check. |
| `unauthorized`, `invalid_api_key`, `key_revoked` | 401 | Missing, unknown or revoked API key. |
| `trial_exhausted` | 402 | The free API trial (5,000 words) cannot cover this request. Includes `wordsNeeded`, `trialRemaining`, `trialTotal` and `billingUrl`. Buy words or choose an API plan; test keys stay free. Nothing is charged. |
| `insufficient_words` | 402 | Not enough words across all sources. Includes `wordsNeeded`, `wordsAvailable`, `balances` and `billingUrl`. Nothing is charged. |
| `key_cap_reached` | 402 | This key's monthly word cap would be exceeded. Includes `wordsNeeded`, `monthlyWordCap` and `usedThisMonth`. Raise the cap in the developer portal. |
| `email_unverified` | 403 | Verify the account's email address first. |
| `forbidden` | 403 | The key cannot access this resource. |
| `not_found` | 404 | No such scan for this key. |
| `rate_limited` | 429 | Too many requests. The client exposes `Retry-After` as `getRetryAfterSeconds()`. |
| `internal_error` | 500 | Something went wrong on our side. |
| `workers_unavailable` | 503 | Checks are temporarily unavailable. Retry later. |
| `plan_required` | 403 | **Deprecated, no longer returned.** Free accounts now get a free API trial and free test keys. `ApiException.PLAN_REQUIRED` is kept (marked `@Deprecated`) so existing code still compiles. |

`waitForScan` throws `com.plagiarismcheckerplus.sdk.ScanTimeoutException` when it runs out of time. A scan whose status is `failed` is returned, not thrown, so check `status`.

## Notes

- **An AI score is a likelihood, not proof** that someone used AI. A similarity score does not decide on its own whether text is plagiarism. Review the result yourself, and do not use either score alone to accuse anyone.
- Test-key scans carry `"sandbox": true` and contain sample data, not a real check.
- Each tool you run is billed at the full word count.
- Plagiarism Checker API overview: https://plagiarismcheckerplus.com/integrations/api
- AI Detector API overview: https://plagiarismcheckerplus.com/ai-detector-api
- API pricing: https://plagiarismcheckerplus.com/api-pricing
- API reference (try requests in the browser): https://app.plagiarismcheckerplus.com/api-docs
- Developer portal (keys, usage, billing, webhooks): https://app.plagiarismcheckerplus.com/developers
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
