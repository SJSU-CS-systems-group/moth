# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Moth is a Mastodon-compatible server implemented in Spring Boot WebFlux (reactive Java 17). It implements the REST APIs documented at https://docs.joinmastodon.org/ so standard Mastodon clients work with this server. The project also supports ActivityPub federation with HTTP signature verification.

## Build and Test Commands

```bash
# Build the project (produces moth-server.jar)
mvn package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=StatusControllerTest

# Run a single test method
mvn test -Dtest=StatusControllerTest#testMethodName

# Run the server (requires config file - DO NOT commit config files)
java -jar server/target/moth-server.jar path/to/config.properties
```

Tests use embedded MongoDB (Flapdoodle) so no external database is needed for testing.

## Project Structure

Multi-module Maven project:
- **server/** - Main Mastodon REST API server
- **client/** - CLI admin utility for user management
- **util/** - Shared utilities (WebFinger, HttpSignature, Email)

Server source layout (`server/src/main/java/edu/sjsu/moth/server/`):
- **controller/** - REST API endpoints (@RestController)
- **service/** - Business logic and database operations
- **db/** - MongoDB repositories and record classes (no logic here)
- **activitypub/** - ActivityPub federation support (OutboxService, WebfingerService, message types)
- **generated/** - Auto-generated classes from JSON schemas (see bin/json2java.sh)

## Code Conventions

- Use records and avoid getters/setters unless overriding behavior is needed
- Name controller functions based on endpoints: `/api/v1/follow_requests` → `followRequests()`, `/api/v2/follow_requests` → `followRequestsV2()`
- Use `bin/json2java.sh` to generate Java classes from JSON (run in temp directory, check for conflicts with existing classes)
- If modifying a generated file, update the comment at the top so changes aren't overwritten

## Client CLI Usage

The moth-client CLI manages users and email registrations:

```bash
# Build client
mvn package -pl client

# WebFinger lookup
java -jar client/target/moth-client.jar webfinger user@host

# List registered emails
java -jar client/target/moth-client.jar listEmails config.properties

# Create/update user (dry run by default)
java -jar client/target/moth-client.jar updateEmail config.properties email@example.com -u username password --no-dryrun

# Delete email registration
java -jar client/target/moth-client.jar deleteEmail config.properties email@example.com --no-dryrun
```

## Server Configuration

Example config file (DO NOT commit):
```properties
server.port=3333
server.name=moth.example.com
db=mongodb.example.com
account=tooth
smtp.server=smart_host:port
smtp.localPort=2525
```

## Integration Testing

Use `madonctl` (Linux CLI client) for integration testing against a running server.

### Automated madonctl Compatibility Tests

Run the compatibility test script to verify all madonctl commands work:

```bash
# Start the server first
java -jar server/target/moth-server.jar path/to/config.properties

# In another terminal, run the test suite
./bin/test-madonctl-compatibility.sh http://localhost:3333

# Optional: Set a test account ID for relationship tests
TEST_ACCOUNT_ID=someaccountid ./bin/test-madonctl-compatibility.sh
```

The script tests:
- **Phase 1**: Status operations (show, favourite, boost, context, bookmark, pin, mute)
- **Phase 2**: Account relationships (block, mute, lists)
- **Phase 3**: Timelines (hashtag, lists)
- **Phase 4**: Notifications

### Manual madonctl Testing

```bash
# Configure madonctl for your server
madonctl config

# Test individual commands
madonctl status show <status_id>
madonctl status favourite <status_id>
madonctl status boost <status_id>
madonctl timeline --hashtag test
madonctl lists
madonctl notifications
```
