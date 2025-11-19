# Testing Requirements - Mandatory for All New Implementations

## Core Principle: Test Behavior, Not Results

**Always test behavior** (what code does) rather than results (what it returns).

## Mandatory Rule

All new implementations MUST have tests before merge UNLESS:

- Function is trivial (< 5 lines, no logic)
- Simple getter/setter
- Pure interface/type definition

## Test Behavior, Not Results

✅ Good - Testing Behavior:

- "decrypt recovers original plaintext"
- "wrong password fails to decrypt"
- "same input produces different ciphertext" (randomness)

❌ Bad - Testing Results:

- "returns base64 string"
- "algorithm property is AES-GCM"
- "calls crypto.subtle.encrypt"

## Coverage Requirements

- Maintain or improve coverage
- Critical files: 80%+
- Global minimum: 50%

## Merge Blocked If

- New implementation without tests (unless trivial)
- Coverage decreases
- Tests check implementation instead of behavior
- Critical paths untested

## See Full Guide

Full documentation in this file with examples.
