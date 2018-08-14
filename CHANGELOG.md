# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/) and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

**Note:** Until 1.0 any MAJOR or MINOR release may have backwards-incompatible changes.

## [Unreleased]

Nothing yet.


## [0.4.0] - 2018-08-14

### Added

- Add support for multi-module/sub-project POMs.


## [0.3.1] - 2018-03-05

### Fixed

- Issue with sync wait treating seconds as millis (making wait non-existant).


## [0.3.0] - 2018-02-28

### Added

- Configuration for HTTP connect, read and write timeouts.
- Documentation for enabling debug via `CLOUDSMITH_DEBUG` (env) or `cloudsmith.debug` (property).
- Documentation for how the Maven Wagon actually works and interacts with the Cloudsmith API.

### Changed

- Prefix for synchronisation wait properties changed to `cloudsmith.sync_wait` prefix (not backwards compatible).
- Property for synchronisation wait verbose has dropped the `_ENABLED` suffix.


## [0.2.0] - 2018-02-02

### Added

- More verbosity for synchronisation wait.
- Support for disabling synchronisation wait.
- Support for changing synchronisation wait interval.

### Fixed

- Support for Java archives other than JAR, so EAR/WAR/AAR/etc. will now work.


## [0.1.0] - 2018-01-31

Phase 1 release (initial release).
