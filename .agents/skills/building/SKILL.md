---
name: Building and testing
description: Project-specific guidance for the testing and building of project
metadata:
  opencode/autoinvoke: true
---

## Overview

This skill provides conventions and commands for working on the Codecrafters Redis server implemented in Java.

## Build & test

- Build the project with Maven: `mvn compile`
- Run tests: `mvn verify`
    - to get more info run `mvn verify -Dlog.level=debug`
- Package the project: `mvn package`

## Project conventions

- Source code lives in `src/main/java`.
- The entry-point / runner script is `your_program.sh`.

## Useful commands

- `./your_program.sh` — start the Redis server locally.

