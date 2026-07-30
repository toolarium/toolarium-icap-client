[![License](https://img.shields.io/github/license/toolarium/toolarium-icap-client)](https://github.com/toolarium/toolarium-icap-client/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.toolarium/toolarium-icap-client/1.4.2)](https://search.maven.org/artifact/com.github.toolarium/toolarium-icap-client/1.4.2/jar)
[![javadoc](https://javadoc.io/badge2/com.github.toolarium/toolarium-icap-client/javadoc.svg)](https://javadoc.io/doc/com.github.toolarium/toolarium-icap-client)

# toolarium-icap-client

A full-featured ICAP client library in Java with complete [RFC 3507](https://www.rfc-editor.org/rfc/rfc3507.txt) coverage. Designed for antivirus scanning integration with support for multiple vendors including ClamAV, Sophos, Kaspersky, Trend Micro, ESET, McAfee, F-Secure and C-ICAP.

## RFC 3507 Compliance

This library fully implements the ICAP protocol as specified in [RFC 3507](https://www.rfc-editor.org/rfc/rfc3507.txt):

### ICAP Methods (RFC 3507 §4.8 - §4.10)
- **REQMOD** - Request modification mode for adapting HTTP requests before forwarding
- **RESPMOD** - Response modification mode for post-processing origin server responses
- **OPTIONS** - Query server capabilities, supported methods, preview size and service configuration

### Message Format and Headers (RFC 3507 §4.3 - §4.4)
- **ICAP request line** - Absolute URI format `icap://host:port/service ICAP/1.0`
- **Host header** - Included in every ICAP request
- **Encapsulated header** - Correct byte offsets for all REQMOD and RESPMOD forms: REQMOD sends `req-hdr=0, req-body=N` (HTTP request headers only); RESPMOD sends `req-hdr=0, res-hdr=M, res-body=N` (request headers followed by an HTTP response header block). Both `req-body` and `res-body` response variants are supported.
- **Via header** - Added to encapsulated HTTP requests for surrogate identification
- **Hop-by-hop exclusion** - Connection, Keep-Alive and other hop-by-hop headers are not encapsulated
- **Chunked transfer encoding** - All encapsulated bodies use chunked encoding; headers are not chunked
- **Trailer headers** - Parsed after the zero-length terminating chunk and accessible via `ICAPHeaderInformation.getTrailers()`

### Preview Mode (RFC 3507 §4.5)
- Default preview size of 4096 bytes (RFC recommends clients SHOULD support at least 4096)
- Server-advertised preview size is used when available
- `0; ieof` chunk extension for end-of-preview signaling
- **100 Continue** handling: waits after preview, sends remaining data on 100

### Response Handling (RFC 3507 §4.6 - §4.7)
- **204 No Content** - Sends `Allow: 204` when server supports it; auto-selects or manually configurable
- **ISTag** - Parsed from every ICAP response for service state identification
- **Status codes** - All RFC-defined status codes mapped to specific exceptions:
  - 4xx client errors (400, 404, 405, 408) throw `ICAPRequestException`
  - 5xx server errors (500, 501, 502, 503, 505) throw `UnknownIOException`
  - Both provide `getStatusCode()` and `getICAPHeaderInformation()` for diagnostics

### OPTIONS Response Fields (RFC 3507 §4.10)
- **Options-TTL** - Parsed and used as cache TTL, with fallback to client-configured default
- **Max-Connections** - Applied to connection pool; respects user-defined lower limits
- **Service-ID** - Parsed and exposed via `ICAPRemoteServiceConfiguration`
- **Transfer-Preview / Transfer-Ignore / Transfer-Complete** - File extension lists parsed and accessible

### Connection Management (RFC 3507 §4.1)
- **Persistent connections** - Connection pooling with keep-alive support (opt-in)
- **Connection: close** - Explicit detection of both `close` and `keep-alive` from server responses
- **Default port 1344** - Used when not specified in the ICAP URI

### Authentication (RFC 3507 §7.1)
- **Authorization header** - First-class support via `ICAPRequestInformation.setAuthorization()`
- **Default request information** - Configure authorization once for all requests via `ICAPClient.setDefaultRequestInformation()`

### Security (RFC 3507 §7.2)
- **ICAPS (TLS)** - Secure connections via `icaps://` scheme with hostname verification
- **Upgrade header** - Exposed as read-only constant; `icaps://` is the preferred TLS approach

### Caching (RFC 3507 §5)
- **Cache-Control, Expires, Pragma** - Exposed as read-only constants for server policy inspection via `ICAPHeaderInformation.getHeaders()`

### Vendor Compatibility
Tested with structured threat headers from: ClamAV, Sophos, Kaspersky, Trend Micro, ESET, McAfee, F-Secure and C-ICAP (X-Infection-Found, X-Virus-ID, X-Blocked, X-Virus-Name, X-Block-Reason).


## Built With

* [cb](https://github.com/toolarium/common-build) - The toolarium common build

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/toolarium/toolarium-icap-client/tags). 


### Gradle:

```groovy
dependencies {
    implementation "com.github.toolarium:toolarium-icap-client:1.4.2"
}
```

### Maven:

```xml
<dependency>
    <groupId>com.github.toolarium</groupId>
    <artifactId>toolarium-icap-client</artifactId>
    <version>1.4.2</version>
</dependency>
```

## Thread safety and usage model

The library is designed around two distinct roles:

**`ICAPClientFactory` — shared, thread-safe singleton.**
Holds the OPTIONS response cache and the connection pool. All internal state is guarded by `ConcurrentHashMap` and `volatile` fields. Multiple threads may call `getICAPClient()` concurrently without any external synchronization.

**`ICAPClient` — single-thread, per-request object.**
Each call to `getICAPClient()` returns a lightweight new instance with the cached server configuration already injected. This instance is **not safe for concurrent use by multiple threads**, because per-instance settings (`supportCompareVerifyIdenticalContent`, `setDefaultRequestInformation`) are expected to be configured once before use and not mutated from another thread while a request is in progress.

### Recommended pattern — create a client per request

```java
// Safe to call from any thread at any time.
// The factory OPTIONS cache (TTL-based) makes this lightweight — no round-trip on cache hit.
ICAPClientFactory.getInstance()
    .getICAPClient("icap://localhost:1344/srv_clamav")
    .validateResource(ICAPMode.REQMOD, requestInfo, resource);
```

This is the preferred approach. The factory caches the server's OPTIONS response (keyed by host/port/service) and reuses it until the server-advertised TTL expires. No OPTIONS round-trip occurs on a cache hit, so the cost of calling `getICAPClient()` per request is negligible.

### Alternative — reuse a client within a single thread

A client instance may be reused sequentially within the same thread:

```java
// One-time setup per thread (or per logical scanning session).
ICAPClient client = ICAPClientFactory.getInstance()
    .getICAPClient("icap://localhost:1344/srv_clamav")
    .setDefaultRequestInformation(defaults);

// Reuse sequentially — safe within a single thread.
client.validateResource(ICAPMode.REQMOD, resource1);
client.validateResource(ICAPMode.REQMOD, resource2);
```

### Do not share a single client instance across threads

```java
// WRONG — do not do this.
ICAPClient sharedClient = ICAPClientFactory.getInstance().getICAPClient(...);

// Thread 1
sharedClient.validateResource(...);

// Thread 2 (concurrently) — not safe
sharedClient.validateResource(...);
```

Instead, each thread should call `getICAPClient()` independently. The factory's OPTIONS cache and connection pool are shared automatically.

### Responsibility split at a glance

| | `ICAPClientFactory` | `ICAPClient` |
|---|---|---|
| Thread safety | Thread-safe | Single-thread only |
| Lifetime | Application-scoped (singleton) | Per-request or per-thread |
| OPTIONS cache | Owned here (TTL-based, shared) | Injected at construction |
| Connection pool | Owned here (shared) | Used transparently |
| Per-request config | — | `setDefaultRequestInformation`, `supportCompareVerifyIdenticalContent` |

## Usage

```java
// the ICAP-Server information
String hostName = "localhost";
int port = 1344;
String serviceName = "..."; // e.g. srv_clamav

// the user, request source and the resource
String username = "user";
String requestSource = "file";
File file = new File("build/test-file.com");

try {
    InputStream resourceInputStream = ....;
    ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName)
         .validateResource(ICAPMode.REQMOD, new ICAPRequestInformation(username, requestSource), new ICAPResource(file.getName(), resourceInputStream, file.length()));
    
    // If no exception is thrown the resource can be used and is valid. 

} catch (ContentBlockedException e) { // !!! The resource has to be blocked !!!

    // The e.getMessage() gives technical the proper information. It's already logged by the library.

    // The ICAP header contains structured information about virus.
    ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
    icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_VIOLATIONS_FOUND);
    icapHeaderInformation.getHeaderValues(ICAPConstants.HEADER_KEY_X_INFECTION_FOUND);

    // The e.getContent() contains the returned error information from the ICAP-Server.
    // It can be ignored as long as the resource is blocked; otherwise it gives a well structured response.

} catch (ICAPRequestException e) { // 4xx client error (RFC 3507 §4.3.3)

    // The request was rejected by the ICAP server (e.g. bad request, method not allowed, timeout).
    int statusCode = e.getStatusCode(); // e.g. 400, 404, 405, 408
    ICAPHeaderInformation icapHeaderInformation = e.getICAPHeaderInformation();
    LOG.warn("ICAP request error: " + e.getMessage());

} catch (UnknownIOException uioe) { // 5xx server error (RFC 3507 §4.3.3)

    // The ICAP server encountered an error (e.g. server error, service overloaded).
    int statusCode = uioe.getStatusCode(); // e.g. 500, 501, 502, 503, 505
    ICAPHeaderInformation icapHeaderInformation = uioe.getICAPHeaderInformation();
    LOG.warn("ICAP server error: " + uioe.getMessage() + ", headers: " + icapHeaderInformation.getHeaders());

} catch (IOException ioe) {  // I/O error

    LOG.warn("Resource could not be accessed: " + ioe.getMessage(), ioe);

}
```

### Log output of a valid resource (log level INFO):
```
DD8DEE46 - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
30AC31B0 - Validate resource (username: user, source: file, resource: test-file.com, length: 71)
30AC31B0 - Valid resource (username: user, source: file, resource: test-file.com, length: 71, http-status: 204).
```

### Log output of an invalid resource which should be blocked (log level INFO):
```
E1C57BCF - Valid service [200/OK], allow 204: true, available methods: [RESPMOD, REQMOD]
DA054425 - Validate resource (username: user, source: file, resource: test-virus-file.com, length: 70)
DA054425 - Threat found in resource (username: user, source: file, resource: test-virus-file.com, length: 70, http-status: 200):
- X-Infection-Found: [Type=0, Resolution=2, Threat=Eicar-Signature]
- X-Violations-Found: [1, -, Eicar-Signature, 0, 0]
- X-Request-Message-Digest: [{SHA-256}8b3f191819931d1f2cef7289239b5f77c00b079847b9c2636e56854d1e5eff71]
- X-Response-Message-Digest: [{SHA-256}2e124ff42640aafcc7e267269dd495f35411ce469ec2a64c9af56ccd74bed32f]
- X-Resource-Identical-Content: [false]
```

## ICAPResource lifecycle

`ICAPResource` wraps a live `InputStream` and is **single-use**. The client reads from the stream during `validateResource` and marks the resource consumed at that point. Any subsequent call to `validateResource` with the same object throws `IOException` immediately, before any network activity.

```java
ICAPResource resource = new ICAPResource(file.getName(), inputStream, file.length());

// First call — succeeds, resource is now consumed.
client.validateResource(ICAPMode.REQMOD, requestInfo, resource);

// Second call with the same object — throws IOException.
client.validateResource(ICAPMode.REQMOD, requestInfo, resource); // throws!
```

### Retry pattern

Create a new `ICAPResource` for each attempt:

```java
for (int attempt = 0; attempt < 3; attempt++) {
    // Re-open the stream for every attempt.
    try (InputStream stream = new FileInputStream(file)) {
        ICAPResource resource = new ICAPResource(file.getName(), stream, file.length());
        try {
            client.validateResource(ICAPMode.REQMOD, requestInfo, resource);
            break; // success
        } catch (IOException e) {
            if (attempt == 2) throw e;
        }
    }
}
```

If the content is already in memory as a `byte[]`, wrap it in a `ByteArrayInputStream` — a new instance can be created cheaply for each attempt:

```java
byte[] content = ...;
for (int attempt = 0; attempt < 3; attempt++) {
    ICAPResource resource = new ICAPResource("upload.bin",
            new ByteArrayInputStream(content), content.length);
    try {
        client.validateResource(ICAPMode.REQMOD, requestInfo, resource);
        break;
    } catch (IOException e) {
        if (attempt == 2) throw e;
    }
}
```

> **Why single-use?** Reusing a consumed stream would send `(length - previewSize)` bytes while declaring the full original `Content-Length`, causing the server to scan a truncated body and potentially issue a clean verdict for content it never fully received.

You can inspect the consumed state explicitly if needed:

```java
resource.isConsumed(); // false before validateResource, true after
```


## Authentication

ICAP authentication (RFC 3507 §7.1) is supported via the `Authorization` header. It can be configured once per client using default request information, or per-request:

```java
// Configure once for all requests
ICAPRequestInformation defaults = new ICAPRequestInformation();
defaults.setAuthorization("Basic dXNlcjpwYXNz");

ICAPClient client = ICAPClientFactory.getInstance().getICAPClient("icap://localhost:1344/srv_clamav");
client.setDefaultRequestInformation(defaults);

// all subsequent calls use the authorization automatically
client.validateResource(ICAPMode.REQMOD, resource);

// or override per-request
ICAPRequestInformation perRequest = new ICAPRequestInformation();
perRequest.setAuthorization("Bearer mytoken123");
client.validateResource(ICAPMode.REQMOD, perRequest, resource);
```

## OPTIONS response fields

The library parses the following server-advertised fields from the OPTIONS response (RFC 3507 §4.10):

| Field | Description |
|---|---|
| `Options-TTL` | Cache TTL in seconds. Used instead of the client-configured default when available. |
| `Max-Connections` | Maximum parallel connections. Applied to the connection pool automatically. |
| `Service-ID` | Service identifier. |
| `Transfer-Preview` | File extensions for which preview is useful. |
| `Transfer-Ignore` | File extensions to transfer without preview. |
| `Transfer-Complete` | File extensions to always transfer completely. |

These values are accessible via `ICAPRemoteServiceConfiguration` after calling `options()`.

### Read-only headers

The following RFC 3507 headers are exposed as constants in `ICAPConstants` for inspection via `ICAPHeaderInformation.getHeaders()`, but the library does not act on them:

| Header | RFC Section | Description |
|---|---|---|
| `Upgrade` | §7.2 | In-band TLS negotiation. Use `icaps://` for TLS instead. |
| `Cache-Control` | §5 | Server-advertised caching policy for ICAP responses. |
| `Expires` | §5 | Expiration time for cached ICAP responses. |
| `Pragma` | §5 | Caching directives (e.g. `no-cache`). |

```java
// Example: inspect server caching policy
ICAPHeaderInformation info = client.validateResource(ICAPMode.REQMOD, resource);
List<String> cacheControl = info.getHeaderValues(ICAPConstants.HEADER_KEY_CACHE_CONTROL);
```


## Connection pooling
By default, each request creates a new TCP connection. For high-throughput scanning, connection pooling can be enabled to reuse sockets:

```java
// Enable pooling with up to 5 idle connections per host
ICAPClientFactory.getInstance().getICAPConnectionManager().setMaxPoolConnectionsPerHost(5);

// Optionally set idle timeout (default: 60s)
ICAPClientFactory.getInstance().getICAPConnectionManager().setPoolIdleTimeout(30000);

// Shut down the pool when done
ICAPClientFactory.getInstance().closePool();
```

> **Note:** Connection pooling requires the ICAP server to support keep-alive connections. Pooling is disabled by default (pool size 0). Sockets are only reused when the server responds with `Connection: keep-alive`.


## Secure connection
To support secured connection you can either pass by the getICAPClient a icaps:// url or enable the secure connection by

```java
ICAPClientFactory.getInstance().getICAPClient(hostName, port, serviceName, true).validateResource(....);
```

For additional requirements regarding the connection the ICAPConnectionManager can bet set in the ICAPClientFcatory. 
The simplest way is to extend the default implementation ``com.github.toolarium.icap.client.impl.ICAPConnectionManagerImpl`` and overwrite the 
``createSecureSocket`` or the ``createUnsecureSocket`` method. 



## Test 
```
# start service - after start you can use the java library
docker run --rm --name icap-server -p 1344:1344 toolarium/toolarium-icap-calmav-docker:0.0.1

or by cb-container:

cb-container --start toolarium/toolarium-icap-calmav-docker -p 1344

# optional you can login into the container
docker exec -it icap-server /bin/bash

# the configuration you will see under
more /etc/c-icap/c-icap.conf

# view / tail access-log
tail -f /var/log/c-icap/access.log

# view / tail server-log
tail -f /var/log/c-icap/server.log

# test with c-icap client inside the container
c-icap-client -v -f entrypoint.sh -s "srv_clamav" -w 1024 -req http://request -d 5

# stop service
docker stop icap-server
```

## Additional resources found on the web
[ICAP filtering](https://www.openidentityplatform.org/blog/icap-filter-openig)
[airlock & icap](https://docs.airlock.com/gateway/7.4/#data/icap.html)
[Freshcalm](https://linux.die.net/man/1/freshclam)
[free container](https://github.com/freeipa/freeipa-container)