# toolarium-icap-client

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [ 1.4.0 ] - 2026-05-24
### Added
- Added UnknownIOException with statusCode field to access ICAP response headers on server error status codes (PR #25, issue #12).
- Added ICAPRequestException for 4xx client errors (RFC 3507 §4.3.3) with statusCode and ICAPHeaderInformation.
- Added granular error status code handling: 400, 404, 405, 408 throw ICAPRequestException; 500, 501, 502, 503, 505 throw UnknownIOException with descriptive messages.
- Added connection pooling support (disabled by default, opt-in via setMaxPoolConnectionsPerHost).
- Added RFC 3507 §4.10 OPTIONS response parsing: Options-TTL, Max-Connections, Service-ID, Transfer-Preview, Transfer-Ignore, Transfer-Complete.
- Added Options-TTL support: cache TTL is now derived from the server-advertised Options-TTL when available, falling back to the client-configured default.
- Added Max-Connections enforcement: pool size is reduced when it exceeds the server-advertised limit (logged as warning); user-defined lower limits are kept (logged as info).
- Added first-class Authorization header support for ICAP authentication (RFC 3507 §7.1) via ICAPRequestInformation.setAuthorization().
- Added ICAPClient.setDefaultRequestInformation() to configure authorization, user agent, timeouts etc. once for all requests.

### Fixed
- Fixed ClassCastException in ChunkedInputStream when CR is followed by non-LF byte.
- Fixed duplicate readHeader() in ChunkedInputStream causing REQMOD body parsing errors (issue #12).
- Fixed chunk extension handling per RFC 2616 Section 3.6.1, e.g. "0; ieof" (issue #12).
- Fixed socket and stream leak when close() encounters flush failure.
- Fixed NPE in ICAPHeaderInformation.containsHeader() when headers not yet initialized.
- Fixed DigestInputStream not being closed after resource transfer.
- Fixed temp file deletion not guaranteed; added deleteOnExit fallback.
- Fixed race condition in service cache causing duplicate OPTIONS requests.
- Fixed RuntimeExceptions being silently swallowed in processContent.
- Fixed REQMOD response encapsulated body parsing: server error responses with res-body are now correctly read (RFC 3507 §4.4.1).

### Changed
- Increased default preview size from 1024 to 4096 bytes (RFC 3507 §4.5 SHOULD).
- Added trailer header support after chunked body terminator (RFC 3507 §4.3.1).
- Added Upgrade, Cache-Control, Expires, Pragma, Date, Trailer header constants for RFC 3507 §4.3.1 / §5 / §7.2 compliance.
- Added Via header to encapsulated HTTP requests for surrogate identification (RFC 3507 §4.3).
- Improved Connection header handling: explicit detection of both "close" and "keep-alive" from server responses (RFC 3507 §4.1).
- Set default socket timeouts to 30s (connection) and 60s (read) to prevent thread hangs.
- Improved request identifier uniqueness using UUID instead of String.hashCode().
- SHA256 digest computation now only performed when compare/verify is enabled.
- Increased internal copy buffer size from 1024 to 8192 bytes.
- Replaced String concatenation with StringBuilder in hot paths.
- Limited hex dump in debug logging to 256 bytes per chunk.
- Reuse ByteArrayOutputStream in header parsing loop to reduce allocations.
- Limited threat response file reading to 64KB to prevent OOM on large responses.
- Use Files.createTempFile for secure temp file creation.
- Explicit options() call now forces refresh instead of returning stale cache.
- Added retry logic (up to 3 retries with backoff) for transient connection failures.
- Improved cache duration log calculation.

### Security
- Enabled TLS hostname verification for icaps:// connections using HTTPS endpoint identification algorithm.
- Added explicit startHandshake() to surface certificate errors on connect.
- Fixed CRLF header injection via custom headers, User-Agent and RequestSource.
- Added Transfer-Encoding and Content-Length to custom header blocklist.
- Added bounds on header parsing (max 128 headers, 64KB total) to prevent OOM.
- Sanitized resource names in log output to prevent log injection.

## [ 1.3.9 ] - 2025-04-07
### Fixed
- Bugfix issue resource with no name (null or empty).

## [ 1.3.8 ] - 2025-02-17
### Added
- Added getHeaders on ICAPRemoteServiceConfiguration (issue #19).

### Changed
- Refactoring of maxRequestTimeout to differentiate between connection and read timeout.

## [ 1.3.7 ] - 2025-01-26
### Changed
- Refactoring method setSocketTimeout into setDefaultSocketTimeout on the ICAPConnectionManager to set a default max connection timeout.
- Added max request timeout on ICAPRequestInformation (issue #18).
- Update method for custom headers.

### Fixed
- Bugfix issue #17 to propagate IOExectption in case a connection is failed.

## [ 1.3.6 ] - 2025-01-25
### Added
- Added support of custom headers. The reserved headers such as Host, Connection, User-Agent, Preview, Encapsulated, Allow can not be overwritten.

### Fixed
- Bugfix issue #15 to handle socket timeout properly.

## [ 1.3.5 ] - 2024-12-11
### Added
- Added method setSocketTimeout on ICAPConnectionManager (see #11).

### Fixed
- Issue "wrong host" (see #13).

## [ 1.3.4 ] - 2024-11-04
### Changed
- Update libraries.

### Fixed
- Creation of icaps connection by ICAPClientFactory. 

## [ 1.3.3 ] - 2024-04-14
### Changed
- Update libraries.

## [ 1.3.2 ] - 2023-08-28
### Fixed
- Proper handling an empty file.

## [ 1.3.1 ] - 2023-08-01
### Added
- Broader support of different Virus scanners.
- McAffee test and support by @techorix.

## [ 1.3.0 ] - 2023-05-11
### Added
- Added switch to enable or disable compare input/output content and set header field. 
  In many cases the server don't let you compare it (new by default=false).

### Fixed
- Issues resource handling.

## [ 1.2.0 ] - 2022-11-12
### Added
- FILEMOD support.
- REQMOD proper support (Bugfix #6).

### Fixed
- Bugfix #5: Bugfix non default port issue.
- Bugfix #7: Typo in log.

## [ 1.1.0 ] - 2022-04-07
### Added
- Support for secure connection, e.g. icaps://<hostname>:<port>/srv_clamav.

## [ 1.0.2 ] - 2022-04-04
### Fixed
- Send properly remaining file size.

## [ 1.0.1 ] - 2021-10-22
### Added
- Support for connecting by a url string ICAPClientFactory, e.g. icap://<hostname>:<port>/srv_clamav.

## [ 1.0.0 ] - 2021-10-21
### Changed
- Setup initial version.
