# Delirium Workspace Rules

## Project Identity
You are working on **Delirium**, a zero-knowledge paste system where security and privacy are paramount. The server NEVER sees unencrypted content.

## Core Principles

### 1. Zero-Knowledge Architecture

- ALL encryption happens client-side before data leaves the browser
- Encryption keys are NEVER transmitted to the server
- Keys exist only in URL fragments (after `#`) which browsers don't send
- Server stores only encrypted blobs (ciphertext + IV)

### 2. Security First

- Never log sensitive data (keys, plaintext, tokens)
- Never expose internal error details to clients
- Always validate inputs on both client and server
- Follow principle of least privilege
- Use AES-256-GCM for authenticated encryption

### 3. Type Safety

- Use TypeScript strict mode everywhere
- Prefer explicit types over inference
- Export main functions for testing
- Add JSDoc comments for public APIs

## Code Style

### TypeScript (client/)
```typescript
// ? Good: Explicit types, clear naming
export async function encryptPaste(
  plaintext: string, 
  key: CryptoKey
): Promise<{ ct: string; iv: string }> {
  // Implementation
}

// ? Bad: Implicit types, unclear
export async function encrypt(text, k) {
  // Implementation
}
```

### Kotlin (server/)
```kotlin
// ? Good: Data classes, suspend functions
data class Paste(
    val id: String,
    val ct: String,
    val iv: String,
    val meta: PasteMeta
)

suspend fun getPaste(id: String): Paste? {
    // Implementation
}

// ? Bad: Mutable properties, blocking calls
class Paste {
    var id: String = ""
    var ct: String = ""
}
```

## Testing Requirements

### Coverage

- Minimum 85% code coverage for CI to pass
- Critical functions (encryption, auth) require 100%
- Test error paths and edge cases

### Test Organization
```
client/tests/
??? unit/          # Fast, isolated (*.test.ts)
??? integration/   # API endpoints (*.test.ts)
??? e2e/           # Full user flows (*.spec.ts)
```

### Test Style
```typescript
// ? Good: Descriptive, grouped, isolated
describe('Paste Encryption', () => {
  it('should encrypt with AES-256-GCM', async () => {
    const key = await generateKey();
    const { ct, iv } = await encryptPaste('test', key);
    expect(ct).toBeTruthy();
    expect(iv).toHaveLength(24); // Base64 encoded 16 bytes
  });
});

// ? Bad: Vague, dependent on other tests
it('works', () => {
  const result = doSomething();
  expect(result).toBeTruthy();
});
```

## API Design

### Endpoints
```
POST /api/pastes          # Create paste
GET  /api/pastes/:id      # Retrieve paste
DELETE /api/pastes/:id    # Delete paste (requires token)
GET  /api/pow             # Get proof-of-work challenge
```

### Request/Response
```typescript
// POST /api/pastes
interface CreatePasteRequest {
  ct: string;              // Base64 ciphertext
  iv: string;              // Base64 initialization vector
  meta: {
    singleView?: boolean;  // Self-destruct after view
    expiresAt?: number;    // Unix timestamp
  };
  pow?: {                  // Proof of work (if required)
    challenge: string;
    nonce: number;
    hash: string;
  };
}

interface CreatePasteResponse {
  id: string;              // Paste ID
  deleteToken: string;     // For deletion
}
```

## File Organization

### Where Things Go
```
client/src/*.ts           ? TypeScript sources (READ THESE)
client/js/*.js            ? Compiled output (PREFER .ts files)
server/src/main/kotlin/   ? Kotlin sources
server/build/             ? Build artifacts (IGNORE)
docs/                     ? Documentation
scripts/                  ? Deployment/utility scripts
```

### What to Ignore

- `node_modules/` - Dependencies (use package.json)
- `build/`, `dist/` - Generated files
- `*.log` - Log files
- `coverage/` - Test coverage reports
- `.gradle/` - Gradle cache

## Common Patterns

### Client-Side Encryption Flow
```typescript
// 1. Generate key
const key = await crypto.subtle.generateKey(
  { name: 'AES-GCM', length: 256 },
  true,
  ['encrypt', 'decrypt']
);

// 2. Encrypt
const iv = crypto.getRandomValues(new Uint8Array(16));
const ciphertext = await crypto.subtle.encrypt(
  { name: 'AES-GCM', iv },
  key,
  encoder.encode(plaintext)
);

// 3. Upload (without key!)
const response = await fetch('/api/pastes', {
  method: 'POST',
  body: JSON.stringify({
    ct: arrayBufferToBase64(ciphertext),
    iv: arrayBufferToBase64(iv)
  })
});

// 4. Share URL with key in fragment
const keyData = await crypto.subtle.exportKey('raw', key);
const keyB64 = arrayBufferToBase64(keyData);
const shareUrl = `${location.origin}/view.html#${pasteId}:${keyB64}`;
```

### Server-Side Validation
```kotlin
// Always validate, never trust client
fun validatePaste(paste: CreatePasteRequest): Result<Unit> {
    return when {
        paste.ct.isEmpty() -> 
            Result.failure("Ciphertext required")
        paste.ct.length > MAX_CT_LENGTH -> 
            Result.failure("Paste too large")
        !isValidBase64(paste.ct) -> 
            Result.failure("Invalid ciphertext encoding")
        !isValidBase64(paste.iv) -> 
            Result.failure("Invalid IV encoding")
        else -> Result.success(Unit)
    }
}
```

## Error Handling

### Client
```typescript
// ? Good: User-friendly, don't expose internals
try {
  await uploadPaste(data);
} catch (error) {
  showError('Failed to create paste. Please try again.');
  console.error('Upload error:', error); // Debug only
}

// ? Bad: Exposes internals, scary for users
catch (error) {
  alert(error.message); // Might show internal details
}
```

### Server
```kotlin
// ? Good: Log details, return generic message
try {
    val paste = storage.getPaste(id)
    call.respond(paste)
} catch (e: Exception) {
    logger.error("Failed to retrieve paste $id", e)
    call.respond(HttpStatusCode.InternalServerError, 
        ErrorResponse("Failed to retrieve paste"))
}

// ? Bad: Leaks internal details
catch (e: Exception) {
    call.respond(HttpStatusCode.InternalServerError, 
        ErrorResponse(e.message ?: "Error"))
}
```

## Deployment

### Local Development
```bash
make quick-start    # First time setup
make dev            # Development mode
make test           # Run all tests
```

### CI Verification
```bash
./scripts/ci-verify-all.sh      # All checks
./scripts/ci-verify-quick.sh    # Quick iteration
```

### Production
```bash
./QUICK_DEPLOY.sh   # VPS with SSL
make start          # Local Docker
```

## Git Workflow

### Branches

- `main` - Production ready
- `draft/*` - Feature branches for PR
- Use descriptive names: `draft/security-ux-bundle`

### Commits
- Clear, descriptive messages
- Group related changes
- Reference issues when applicable
- Use contextual commit messages that align with branch purpose

#### Contextual Commit Messages
Match commit messages to branch context:

```
Branch: feature/cursor-rules-migration


? Good commits:

- "feat: add AI collaboration workflow rules"
- "docs: update cursor migration guide"
- "refactor: reorganize workspace rules structure"

? Bad commits (off-context):

- "fix: rate limiting bug" (unrelated to cursor rules)
- "feat: add new API endpoint" (wrong feature)

Branch: draft/security-ux-bundle


? Good commits:

- "feat: add rate limiting middleware"
- "feat: improve error message clarity"
- "test: add security validation tests"

? Bad commits (off-context):

- "docs: update deployment guide" (not security/UX)
- "refactor: rename utility functions" (unrelated)

Branch: fix/encryption-padding-bug


? Good commits:

- "fix: correct AES-GCM padding calculation"
- "test: add padding edge case coverage"
- "docs: document encryption padding behavior"

? Bad commits (off-context):

- "feat: add new encryption algorithm" (scope creep)
- "refactor: rewrite entire crypto module" (too broad)
```

**Commit Message Format:**
```
<type>: <description>

[optional body explaining why, not what]

[optional footer with references]
```

**Types:**


- `feat:` - New feature (aligns with feature/* or draft/* branches)
- `fix:` - Bug fix (aligns with fix/* branches)
- `docs:` - Documentation only
- `test:` - Adding or updating tests
- `refactor:` - Code change that neither fixes bug nor adds feature
- `chore:` - Build process, dependencies, tooling
- `perf:` - Performance improvement
- `style:` - Code style/formatting (no logic change)

**Context Validation:**


Before committing, ask:
1. Does this commit belong on this branch?
2. Does the commit type match the branch purpose?
3. Is this change part of the branch's stated goal?
4. Should this be a separate branch/PR instead?

### Quality Gates
All CI checks must pass:


- ? Linting (ESLint)
- ? Type checking (tsc)
- ? Tests (Jest, Playwright)
- ? Coverage (85% minimum)
- ? Security audit

## API Contract & Backward Compatibility

### Core Principle: Never Break Existing Contracts

**CRITICAL RULE**: When encountering issues with tests or integrations, **ALWAYS** investigate and fix the issue in the test/consumer code, NOT by changing the API contract.

### API Contract Rules

#### 1. API First, Tests Second
```
? BAD Approach:
- Test fails
- Change API signature to match test expectations
- Break all existing code using the API

? GOOD Approach:
- Test fails
- Check how API is actually used in production code
- Fix test to match actual API contract
- Understand WHY the API was designed that way
```

#### 2. When You Can Change APIs
Only change API contracts when:


- **New feature**: Adding NEW functionality (additive changes)
- **Deprecation cycle**: Old API + new API coexist with warnings
- **Breaking change**: Documented, versioned, with migration guide
- **Bug fix**: The API itself has a design flaw (rare, needs review)

#### 3. Investigation Checklist
Before changing any public API:


- [ ] Search codebase for all usages: `grep -r "functionName"` 
- [ ] Check how it's actually called in production code
- [ ] Verify the return type/parameters in real usage
- [ ] Read comments/docs explaining the design decision
- [ ] Consider if tests are wrong, not the API

#### 4. Examples

**Example 1: Test Expects Wrong Type**
```typescript
// Production API (ArrayBuffer-based for crypto operations)
export async function encryptWithPassword(
  content: string, 
  password: string
): Promise<{ encryptedData: ArrayBuffer; salt: ArrayBuffer; iv: ArrayBuffer }> {
  // ... implementation
}

// Used in app.ts like this:
const { encryptedData, salt, iv } = await encryptWithPassword(text, password);
const ctB64 = b64u(encryptedData); // Convert to base64 for transport

// ? BAD: Change API to return base64 strings to match test expectations
// ? GOOD: Fix test to work with ArrayBuffers like production code does
```

**Example 2: Function Signature Mismatch**
```typescript
// API returns { success: boolean, data: string }
async function getData(): Promise<{ success: boolean; data: string }> { ... }

// Test expects just string
// ? BAD: Change API to return string
// ? GOOD: Fix test to destructure { success, data }
```

#### 5. Documentation Requirements
All public APIs must have:
```typescript
/**
 * Brief description
 * 
 * @param paramName Description and WHY this type/format
 * @returns Description and WHY this type/format
 * @example
 * // Show actual usage
 * const result = await encryptWithPassword('text', 'pass');
 * // result.encryptedData is ArrayBuffer for binary data
 */
```

#### 6. Breaking Changes Process
If you MUST break an API:


1. Create issue documenting the breaking change
2. Add deprecation warning to old API
3. Implement new API alongside old one
4. Update all internal usages
5. Write migration guide
6. Bump major version
7. Keep old API for 1-2 versions

### Type Safety & Contracts
```typescript
// ? GOOD: Explicit types enforce contracts
export async function encryptWithPassword(
  content: string,
  password: string
): Promise<{
  encryptedData: ArrayBuffer;  // Binary data
  salt: ArrayBuffer;           // For PBKDF2
  iv: ArrayBuffer;             // For AES-GCM
}>

// ? BAD: Implicit returns hide contracts
export async function encryptWithPassword(content, password) {
  return { ct: '...', iv: '...', salt: '...' }; // What types?
}
```

## AI Collaboration Workflow

When working with AI assistants (like Cursor AI):

### Pull Request Philosophy
- **Small PRs are better**: Break work into focused, reviewable chunks
- **One concern per PR**: Single feature, bug fix, or refactor
- **Atomic changes**: Each PR should be independently deployable
- **Clear scope**: Easy to review, test, and understand

### Commit & Push Control


- **NEVER auto-commit**: AI should NOT automatically commit changes
- **NEVER auto-push**: AI should NOT automatically push to remote
- **Manual review required**: Developer reviews all changes before commit
- **Explicit confirmation**: Developer must explicitly ask for commit/push

### AI Assistant Guidelines
```
? DO:
- Make code changes using edit tools
- Suggest commit messages when asked
- Run tests and verify changes
- Create multiple small, focused PRs

? DON'T:
- Automatically git add/commit/push
- Create large, multi-concern PRs
- Commit without explicit developer request
- Push to remote without confirmation
```

### Workflow Example
```bash
# 1. AI makes changes (editing files)
# 2. Developer reviews the changes
# 3. Developer explicitly asks: "commit these changes"
# 4. AI suggests commit message
# 5. Developer approves or modifies
# 6. Developer explicitly asks: "push to remote"
# 7. AI pushes only when told
```

### PR Size Guidelines
```
? Small PR (100-300 lines):
- Add rate limiting to one endpoint
- Refactor encryption utility
- Fix specific bug with tests

? Large PR (1000+ lines):
- Rewrite entire auth system + add UI + update docs
- Multiple unrelated features
- "Security and UX bundle" with 10 different concerns
```

### Breaking Down Large Tasks
When facing a large task:
1. Identify distinct concerns
2. Create separate branches for each
3. Submit PRs sequentially or in parallel
4. Keep each PR focused and testable

Example breakdown:
```
Large task: "Improve security and UX"

Split into:


- PR 1: Add rate limiting (security)
- PR 2: Add input validation (security)
- PR 3: Improve error messages (UX)
- PR 4: Add loading states (UX)
```

## Security Checklist

Before committing:


- [ ] No hardcoded secrets or keys
- [ ] No sensitive data in logs
- [ ] Client-side encryption verified
- [ ] Input validation on server
- [ ] Error messages don't leak internals
- [ ] Tests cover security-critical paths

## Performance

### Client
- Use native Web Crypto API (fast, no dependencies)
- Minimize bundle size
- Lazy load when possible

### Server
- Use connection pooling
- Implement rate limiting
- Efficient database queries
- Enable response compression

## Remember



1. **Zero-Knowledge**: If you're sending a key to the server, you're doing it wrong
2. **Test Everything**: No code without tests
3. **Type Safety**: Explicit types prevent bugs
4. **Security First**: When in doubt, be more secure
5. **User Experience**: Clear errors, fast responses

---

When suggesting code changes:


- Maintain zero-knowledge principle
- Follow existing patterns
- Add tests for new functionality
- Update documentation if needed
- Run CI verification before committing

## Test Coverage Standards

### CRITICAL RULE: Never Decrease Coverage >5%

**When adding new code, test coverage must not drop by more than 5%.**

#### Coverage Enforcement

```bash
# 1. Check baseline before adding code
npm run test:coverage
# Note current %: security.ts: 82.69%, overall: 47.43%

# 2. Add your new feature

# 3. Check coverage after
npm run test:coverage

# 4. Verify delta
# ✅ ACCEPT: Coverage ≥ baseline or drops ≤5%
# ❌ REJECT: Coverage drops >5%
```

#### Coverage Targets
- **Global minimum**: 85% for CI
- **Critical code**: 100% coverage
  - Encryption/decryption
  - Password handling
  - Authentication
  - Input validation
  - Security utilities
- **New code**: Must be tested

### Test Quality Requirements

#### 1. Comprehensive & Well-Documented

Every test MUST have:

**Clear names** describing behavior:
```typescript
// ✅ GOOD
it('should allow 5 password attempts before failing')
it('should preserve view count during password retry')

// ❌ BAD  
it('works')
it('test1')
```

**Arrange-Act-Assert with comments**:
```typescript
it('should not consume views on password retry', async () => {
  // WHY: Users need retries without invalidating paste
  // Data stays in browser RAM, no re-fetch from server
  
  // Arrange
  const fetchSpy = jest.spyOn(global, 'fetch');
  
  // Act
  await viewWithRetry(['wrong', 'correct']);
  
  // Assert: Only 1 fetch (not 2)
  expect(fetchSpy).toHaveBeenCalledTimes(1);
});
```

**Test all paths**:
- ✅ Happy path
- ✅ Edge cases (empty, unicode, boundaries)
- ✅ Error paths
- ✅ Security scenarios

#### 2. Independent Tests

```typescript
// ✅ GOOD: Self-contained
it('should encrypt empty strings', async () => {
  const result = await encrypt('', 'pass');
  expect(result).toBeDefined();
});

// ❌ BAD: Shared state
let data;
it('encrypts', () => { data = encrypt('test'); });
it('decrypts', () => { decrypt(data); }); // Depends!
```

#### 3. Test Behavior, Not Implementation

```typescript
// ✅ GOOD: Tests what it does
it('should decrypt with correct password', async () => {
  const encrypted = await encryptWithPassword('test', 'pass');
  const result = await decryptWithPassword(
    encrypted.encryptedData, 'pass', encrypted.salt, encrypted.iv
  );
  expect(result).toBe('test');
});

// ❌ BAD: Tests how it does it
it('should call crypto.subtle', () => {
  const spy = jest.spyOn(crypto.subtle, 'encrypt');
  encrypt('test');
  expect(spy).toHaveBeenCalled(); // Breaks on refactor!
});
```

### PR Checklist

Before merging new code:

- [ ] All new functions have tests
- [ ] Happy path + edge cases + errors covered
- [ ] `npm run test:coverage` shows ≥85% or <5% drop
- [ ] Tests are readable with clear names
- [ ] Complex logic has "why" comments
- [ ] Tests are independent
- [ ] Security code has 100% coverage
- [ ] If coverage drops, PR explains why

### Coverage Reports

```bash
# Full coverage
npm run test:coverage

# HTML report
npm run test:coverage --coverageReporters=html
open coverage/index.html

# Specific file
npm test -- security.test.ts --coverage
```

### Remember

1. **Coverage is a tool, not a goal** - Quality > Quantity
2. **Test behavior, not implementation**
3. **Make tests readable** - Help future maintainers
4. **Never drop >5% without justification**
5. **Document why tests exist**

See also: `TEST_COVERAGE_RULES.md` for detailed examples

## Zero Untested Code Policy

### CRITICAL RULE: No Untested Code in PRs

**Every PR must include tests for ALL new code that can usefully be tested.**

#### The Rule

```
❌ PROHIBITED: Submitting new code without tests
✅ REQUIRED: Tests submitted alongside the code in the same PR
```

#### What This Means

**If you add new code, you add tests. Period.**

- Adding a new function? **Add tests for it**
- Adding a new feature? **Add tests for it**
- Modifying existing logic? **Add tests for the changes**
- Fixing a bug? **Add a test that would have caught it**

#### Exceptions (Rare)

Only skip tests if code is:
1. **Pure boilerplate** - Empty interfaces, simple types
2. **Configuration only** - JSON/YAML with no logic
3. **Truly untestable** - Requires physical hardware, external services
4. **Temporary scaffolding** - Will be replaced in next commit

**If in doubt, write a test.**

#### PR Review Checklist

Reviewers must verify:

- [ ] Every new function has corresponding tests
- [ ] Every code path is covered by tests
- [ ] Tests exist for both success and failure cases
- [ ] No `// TODO: add tests` comments
- [ ] Coverage hasn't dropped >5%

If any new code lacks tests:
1. **Request changes** - Don't approve
2. **Ask why** - Require justification
3. **Require tests** - Before merging

#### Examples

**✅ GOOD PR:**
```
Files changed:
  src/password-retry.ts  (+45 lines)  # New feature
  tests/password-retry.test.ts  (+120 lines)  # Comprehensive tests

Coverage: 82.69% → 85.30% (+2.61%)  ✅
```

**❌ BAD PR:**
```
Files changed:
  src/password-retry.ts  (+45 lines)  # New feature
  # No test file!

Coverage: 82.69% → 78.50% (-4.19%)  ❌
Comment: "Will add tests later"  ❌❌❌
```

#### "Will Add Tests Later" is NOT Acceptable

```
❌ "I'll add tests in the next PR"
❌ "Tests are hard for this code"
❌ "It's just a small change"
❌ "I tested it manually"
❌ "Coverage is good enough"
```

**The answer is always: Add tests NOW.**

#### Why This Matters

**Without this rule:**


- Coverage slowly decays
- Bugs slip through
- Code becomes "too scary to change"
- Technical debt accumulates
- Future developers suffer

**With this rule:**


- Coverage stays high
- Bugs caught early
- Code stays maintainable
- Confidence in changes
- Quality culture maintained

#### Implementation

**Before submitting PR:**
```bash
# 1. Write your code
vim src/new-feature.ts

# 2. Write tests for your code
vim tests/new-feature.test.ts

# 3. Verify tests pass
npm test

# 4. Verify coverage
npm run test:coverage
# Check that coverage didn't drop >5%

# 5. Submit PR with BOTH code and tests
git add src/new-feature.ts tests/new-feature.test.ts
git commit -m "feat: add feature with comprehensive tests"
```

**During PR review:**
```bash
# 1. Check for test files
git diff --name-only origin/main | grep test

# 2. Verify coverage
npm run test:coverage

# 3. Reject if untested code exists
# Request: "Please add tests for X before I can approve"
```

#### Real Example: Password Retry

**✅ How it was done correctly:**
```
Commit: "feat: improve password and single-view UX"

Added code:


- Password retry logic (5 attempts)
- Single-view UI toggle
- Memory management for retries

Added tests:


- it('should allow 5 password attempts before failing')
- it('should preserve view count during retries')
- it('should disable views input when single-view checked')
- it('should securely clear password after each attempt')

Result: New code + tests in same PR ✅
```

**❌ How NOT to do it:**
```
Commit: "feat: add password retry"
+ 45 lines of new password retry logic
+ 0 lines of tests
Coverage: 82% → 75% (-7%)

Comment: "Tests coming in next PR"
Status: ❌ REJECTED
```

### Remember

**If you write code, you write tests.**

No exceptions. No excuses. No "later".

Tests are not optional. They are part of the feature.

---

See also: `TEST_COVERAGE_RULES.md` for detailed testing standards

## Pre-PR Checklist - MANDATORY

### CRITICAL RULE: Clean Build & All Checks Before PR

**Before creating or pushing to a PR, you MUST run a complete clean build and verify ALL checks pass locally.**

#### Why This Rule Exists

**Problem:** Pushing code that fails CI wastes time:
- CI runs take 5-10 minutes
- Reviewers see red X's
- Have to fix, push again, wait again
- Cycle repeats if you don't test properly

**Solution:** Run the EXACT same checks locally first.

### Pre-PR Command Sequence

Run these commands IN ORDER before every PR:

```bash
# 1. Clean everything
npm run clean  # or manually: rm -rf node_modules dist coverage
./gradlew clean

# 2. Fresh install
cd client && npm install

# 3. Build from scratch
npm run build  # Compile TypeScript

# 4. Run linting
npm run lint  # ESLint must pass

# 5. Run all tests
npm test  # All tests must pass

# 6. Check coverage
npm run test:coverage  # Verify ≥85% or <5% drop

# 7. Type check
npm run typecheck  # TypeScript must compile

# 8. Build server
cd ../server && ./gradlew build

# 9. Verify Docker (optional but recommended)
cd .. && docker-compose up --build -d
# Test the application manually
docker-compose down
```

### Automated Pre-PR Script

Create this script to run all checks:

```bash
#!/bin/bash
# scripts/pre-pr-check.sh

set -e  # Exit on any error

echo "🧹 Starting pre-PR checks..."

# Clean
echo "1️⃣ Cleaning..."
cd client
rm -rf node_modules coverage dist
cd ..

# Install
echo "2️⃣ Installing dependencies..."
cd client
npm install

# Build
echo "3️⃣ Building TypeScript..."
npm run build

# Lint
echo "4️⃣ Running ESLint..."
npm run lint

# Tests
echo "5️⃣ Running tests..."
npm test

# Coverage
echo "6️⃣ Checking coverage..."
npm run test:coverage

# Type check
echo "7️⃣ Type checking..."
npm run typecheck || ./node_modules/.bin/tsc --noEmit

# Server build
echo "8️⃣ Building server..."
cd ../server
./gradlew clean build

echo "✅ All checks passed! Ready to create PR."
echo ""
echo "Next steps:"
echo "1. git add <files>"
echo "2. git commit -m 'your message'"
echo "3. git push"
echo "4. Create PR"
```

### Usage

```bash
# Make it executable (first time only)
chmod +x scripts/pre-pr-check.sh

# Run before every PR
./scripts/pre-pr-check.sh

# Only create PR if it passes ✅
```

### Pre-PR Checklist

Before pushing to PR:

- [ ] Run `./scripts/pre-pr-check.sh` (or manual commands)
- [ ] All commands completed successfully (no errors)
- [ ] ESLint: 0 errors, 0 warnings
- [ ] Tests: 100% passing
- [ ] Coverage: ≥85% or drop ≤5%
- [ ] TypeScript: Compiles with no errors
- [ ] Server: Builds successfully
- [ ] Optional: Test in Docker locally

### What to Do If Checks Fail

**ESLint fails:**
```bash
npm run lint  # See errors
# Fix the errors
npm run lint  # Verify fixed
```

**Tests fail:**
```bash
npm test  # See which tests fail
# Fix the code or tests
npm test  # Verify all pass
```

**Coverage too low:**
```bash
npm run test:coverage  # See what's not covered
# Add tests for uncovered code
npm run test:coverage  # Verify ≥85%
```

**TypeScript errors:**
```bash
npm run typecheck  # See type errors
# Fix type issues
npm run typecheck  # Verify no errors
```

### Never Do This

❌ **BAD:**
```bash
git add .
git commit -m "fix stuff"
git push
# Wait 10 minutes for CI to fail
# See red X on PR
# "Oops, forgot to test"
```

✅ **GOOD:**
```bash
./scripts/pre-pr-check.sh
# All checks pass locally ✅
git add .
git commit -m "fix: resolve linting issues"
git push
# CI passes ✅ on first try
```

### CI/CD Mirrors Local

Your local checks should MATCH what CI runs:

| Check | Local Command | CI Command |
|-------|---------------|------------|
| Lint | `npm run lint` | `npm run lint` |
| Test | `npm test` | `npm test` |
| Coverage | `npm run test:coverage` | `npm run test:coverage` |
| Build | `npm run build` | `npm run build` |
| Type | `npm run typecheck` | `npm run typecheck` |

**If it passes locally, it should pass in CI.**

### Remember

1. **Run checks BEFORE pushing** - Not after
2. **Clean build every time** - No cached artifacts
3. **All checks must pass** - No exceptions
4. **Fix failures immediately** - Don't push broken code
5. **Save everyone time** - Including yourself

---

See also: `scripts/ci-verify-all.sh` for existing CI verification
