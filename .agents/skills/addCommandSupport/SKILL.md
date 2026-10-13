---
name: Add support for new command
description: Adds command class skeleton only + integration test + needed wiring
metadata:
  opencode/autoinvoke: true
---

## RESTRICTIONS

Do not reach to REDIS documentation if not explicitly asked, focus only on adding new skill with eventual given
examples!
Do not add any code to redis.storage.Storage.
Do not do any code for parsing input elements.
Focus only on skeleton implementation. Again do not add any implementation! Skill is not outdated.
Even if example was provided, and you think you were asked to implement real implementation don't!

## Overview

When asked it creates new Command class
e.g. redis.commands.Llen which contain command name, implementation which returns
redis.commands.Messages.errorMessage with value TODO. No implementation, no parsing only TODO!.
Class also needs to get "wired" in redis.commands.Interpreter.interpret.
Integration tests, see as example redis.RedisServerLLenIntegrationTest. So there have to be some happy path scenario and
false number of arguments tests.
This is TDD project so it is good that they will fail, but they have to fail with expected error.
After adding new command test and file remember to add it to git.

