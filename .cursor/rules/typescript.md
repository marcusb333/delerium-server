# TypeScript Rules (Delirium)

## File Patterns
Apply to: `**/*.ts`, `**/*.tsx`

## Strict Mode Always
```typescript
// tsconfig.json should have:
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "strictFunctionTypes": true
  }
}
```

## Type Declarations

### ? Explicit Types for Public APIs
```typescript
// Function parameters and returns
export function encryptPaste(
  plaintext: string,
  key: CryptoKey
): Promise<EncryptResult> {
  // ...
}

// Interfaces for data structures
interface EncryptResult {
  ct: string;
  iv: string;
}

// Constants
const MAX_PASTE_SIZE: number = 1_000_000;
```

### ? Avoid Implicit Any
```typescript
// Bad
function process(data) {  // 'data' is implicitly 'any'
  return data.value;
}

// Good
function process(data: PasteData): string {
  return data.value;
}
```

## Async/Await

### ? Always Await Promises
```typescript
// Good
async function createPaste(text: string): Promise<string> {
  const key = await generateKey();
  const encrypted = await encryptText(text, key);
  return await uploadPaste(encrypted);
}

// Good - all awaited in parallel
async function loadPaste(id: string): Promise<void> {
  const [paste, metadata] = await Promise.all([
    fetchPaste(id),
    fetchMetadata(id)
  ]);
}
```

### ? Don't Forget Await
```typescript
// Bad - missing await
async function createPaste(text: string): Promise<string> {
  const key = generateKey();  // Returns Promise, not CryptoKey!
  return encryptText(text, key);  // Type error!
}

// Bad - unnecessary await
async function getValue(): string {
  return await 'hello';  // No promise to await
}
```

## Error Handling

### ? Type-Safe Errors
```typescript
// Custom error types
class PasteError extends Error {
  constructor(
    message: string,
    public code: string,
    public statusCode: number
  ) {
    super(message);
    this.name = 'PasteError';
  }
}

// Usage with type narrowing
try {
  await uploadPaste(data);
} catch (error) {
  if (error instanceof PasteError) {
    console.error(`Paste error ${error.code}:`, error.message);
  } else if (error instanceof Error) {
    console.error('Unknown error:', error.message);
  } else {
    console.error('Non-error thrown:', error);
  }
}
```

### ? Avoid Unsafe Error Handling
```typescript
// Bad - assumes error structure
catch (error) {
  console.error(error.message);  // Error might not have .message
}

// Bad - any type
catch (error: any) {
  // Defeats type safety
}
```

## Web Crypto API Usage

### ? Proper Crypto Types
```typescript
// Key generation with correct types
async function generateKey(): Promise<CryptoKey> {
  return await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 } as AesKeyGenParams,
    true,  // extractable
    ['encrypt', 'decrypt'] as KeyUsage[]
  );
}

// Encryption with proper parameters
async function encrypt(
  plaintext: string,
  key: CryptoKey
): Promise<{ ciphertext: ArrayBuffer; iv: Uint8Array }> {
  const encoder = new TextEncoder();
  const data = encoder.encode(plaintext);
  const iv = crypto.getRandomValues(new Uint8Array(16));
  
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv } as AesGcmParams,
    key,
    data
  );
  
  return { ciphertext, iv };
}
```

## Array Buffer Conversions

### ? Type-Safe Conversions
```typescript
// ArrayBuffer to Base64
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

// Base64 to ArrayBuffer
function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
```

## DOM Manipulation

### ? Type-Safe DOM Access
```typescript
// Good - with null checks
function updateStatus(message: string): void {
  const statusEl = document.getElementById('status');
  if (statusEl) {
    statusEl.textContent = message;
  }
}

// Good - with type assertion when certain
function initializeApp(): void {
  const form = document.getElementById('paste-form') as HTMLFormElement;
  const textarea = document.getElementById('paste-text') as HTMLTextAreaElement;
  
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    await handleSubmit(textarea.value);
  });
}
```

### ? Unsafe DOM Access
```typescript
// Bad - no null check
function updateStatus(message: string): void {
  document.getElementById('status').textContent = message;  // Could be null!
}

// Bad - wrong type
function getTextarea(): string {
  const el = document.getElementById('text');
  return el.value;  // Error: HTMLElement doesn't have .value
}
```

## Export for Testing

### ? Export Everything Testable
```typescript
// app.ts
export async function encryptPaste(
  text: string
): Promise<EncryptedPaste> {
  // ...
}

export function validatePasteSize(text: string): boolean {
  return text.length <= MAX_PASTE_SIZE;
}

// Main entry point
if (typeof window !== 'undefined') {
  initializeApp();
}
```

## JSDoc Comments

### ? Document Public APIs
```typescript
/**
 * Encrypts plaintext using AES-256-GCM with a random IV.
 * 
 * @param plaintext - The text to encrypt
 * @param key - The AES-256-GCM CryptoKey
 * @returns Object containing base64-encoded ciphertext and IV
 * @throws {Error} If encryption fails
 * 
 * @example
 * ```typescript
 * const key = await generateKey();
 * const { ct, iv } = await encryptPaste('secret', key);
 * ```
 */
export async function encryptPaste(
  plaintext: string,
  key: CryptoKey
): Promise<{ ct: string; iv: string }> {
  // Implementation
}
```

## Constants and Configuration

### ? Type-Safe Constants
```typescript
// Use const assertions for literal types
const PASTE_ENDPOINTS = {
  CREATE: '/api/pastes',
  GET: '/api/pastes',
  DELETE: '/api/pastes'
} as const;

// Use enums for related constants
enum PasteStatus {
  Draft = 'DRAFT',
  Published = 'PUBLISHED',
  Expired = 'EXPIRED',
  Deleted = 'DELETED'
}

// Configuration with interface
interface AppConfig {
  readonly maxPasteSize: number;
  readonly apiBaseUrl: string;
  readonly powEnabled: boolean;
}

const config: AppConfig = {
  maxPasteSize: 1_000_000,
  apiBaseUrl: '/api',
  powEnabled: true
};
```

## No Unused Variables

### ? Clean Code
```typescript
// Good - all variables used
function formatPaste(paste: Paste): string {
  const { id, text, createdAt } = paste;
  return `[${id}] ${text} (${createdAt})`;
}

// Good - underscore for intentionally unused
function handleClick(_event: MouseEvent): void {
  // Event parameter required by type but not used
  doSomething();
}
```

### ? Unused Variables
```typescript
// Bad - unused variable
function formatPaste(paste: Paste): string {
  const { id, text, createdAt, author } = paste;  // author unused
  return `[${id}] ${text} (${createdAt})`;
}
```

## Null Safety

### ? Handle Null/Undefined
```typescript
// Optional chaining
const username = user?.profile?.name ?? 'Anonymous';

// Nullish coalescing
const maxSize = config.maxSize ?? DEFAULT_MAX_SIZE;

// Type guards
function processPaste(paste: Paste | null): void {
  if (!paste) {
    console.error('Paste is null');
    return;
  }
  
  // TypeScript knows paste is not null here
  console.log(paste.id);
}
```

## Avoid Any

### ? Use Specific Types
```typescript
// Good - specific type
async function fetchPaste(id: string): Promise<Paste> {
  const response = await fetch(`/api/pastes/${id}`);
  const data: Paste = await response.json();
  return data;
}

// Good - unknown for truly unknown types
function parseJson(text: string): unknown {
  return JSON.parse(text);
}

// Then narrow the type
function processPasteJson(json: string): Paste {
  const data: unknown = parseJson(json);
  
  if (isValidPaste(data)) {
    return data;  // TypeScript knows it's Paste now
  }
  
  throw new Error('Invalid paste data');
}

function isValidPaste(data: unknown): data is Paste {
  return (
    typeof data === 'object' &&
    data !== null &&
    'id' in data &&
    'ct' in data &&
    'iv' in data
  );
}
```

### ? Avoid Any
```typescript
// Bad - any disables type checking
async function fetchPaste(id: string): Promise<any> {
  const response = await fetch(`/api/pastes/${id}`);
  return await response.json();
}
```

## Remember


- **Explicit over implicit**: Always specify types for public APIs
- **Await your promises**: Don't forget async/await
- **Handle nulls**: Use optional chaining and nullish coalescing
- **Export for tests**: Make functions testable
- **Document complex functions**: Use JSDoc for clarity
- **No unused code**: Keep it clean
- **Type narrowing**: Use type guards for unknown types
