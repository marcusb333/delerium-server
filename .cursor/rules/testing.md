# Testing Rules (Delirium)

## File Patterns

- Unit/Integration: `**/*.test.ts`
- E2E: `**/*.spec.ts`

## Test Structure

### ? Descriptive Test Organization
```typescript
describe('Paste Encryption', () => {
  describe('encryptPaste', () => {
    it('should encrypt text with AES-256-GCM', async () => {
      const key = await generateKey();
      const plaintext = 'Hello, World!';
      
      const { ct, iv } = await encryptPaste(plaintext, key);
      
      expect(ct).toBeTruthy();
      expect(iv).toHaveLength(24); // Base64 of 16 bytes
    });
    
    it('should produce different IVs for same plaintext', async () => {
      const key = await generateKey();
      const plaintext = 'test';
      
      const result1 = await encryptPaste(plaintext, key);
      const result2 = await encryptPaste(plaintext, key);
      
      expect(result1.iv).not.toBe(result2.iv);
    });
    
    it('should throw error for empty plaintext', async () => {
      const key = await generateKey();
      
      await expect(encryptPaste('', key))
        .rejects.toThrow('Plaintext cannot be empty');
    });
  });
});
```

### ? Poor Test Structure
```typescript
// Bad - vague naming
describe('encryption', () => {
  it('works', async () => {
    const result = await encrypt('test');
    expect(result).toBeTruthy();
  });
  
  // Bad - testing multiple things
  it('does everything', async () => {
    const encrypted = await encrypt('test');
    const decrypted = await decrypt(encrypted);
    const uploaded = await upload(encrypted);
    expect(decrypted).toBe('test');
    expect(uploaded).toBeTruthy();
  });
});
```

## Test Isolation

### ? Independent Tests
```typescript
describe('Paste Storage', () => {
  let storage: PasteStorage;
  
  beforeEach(() => {
    // Fresh instance for each test
    storage = new PasteStorage(':memory:');
  });
  
  afterEach(async () => {
    // Clean up
    await storage.close();
  });
  
  it('should store paste', async () => {
    const paste = { ct: 'abc', iv: 'def', meta: {} };
    
    const id = await storage.save(paste);
    
    expect(id).toBeTruthy();
  });
  
  it('should retrieve stored paste', async () => {
    const paste = { ct: 'abc', iv: 'def', meta: {} };
    const id = await storage.save(paste);
    
    const retrieved = await storage.get(id);
    
    expect(retrieved?.ct).toBe('abc');
  });
});
```

### ? Dependent Tests
```typescript
// Bad - tests depend on execution order
describe('Paste Storage', () => {
  const storage = new PasteStorage();  // Shared state!
  let pasteId: string;
  
  it('should store paste', async () => {
    pasteId = await storage.save({ ct: 'abc', iv: 'def' });
    expect(pasteId).toBeTruthy();
  });
  
  it('should retrieve paste', async () => {
    const paste = await storage.get(pasteId);  // Depends on previous test!
    expect(paste).toBeTruthy();
  });
});
```

## Mocking

### ? Mock External Dependencies
```typescript
describe('Paste Upload', () => {
  it('should upload encrypted paste to API', async () => {
    const mockFetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'test-id', deleteToken: 'token' })
    });
    global.fetch = mockFetch as any;
    
    const result = await uploadPaste({ ct: 'abc', iv: 'def' });
    
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/pastes',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      })
    );
    expect(result.id).toBe('test-id');
  });
});
```

## Test Coverage

### ? Cover Critical Paths
```typescript
describe('Password-Based Encryption', () => {
  // Happy path
  it('should encrypt with password', async () => {
    const encrypted = await encryptWithPassword('data', 'password');
    expect(encrypted.ct).toBeTruthy();
  });
  
  // Decryption
  it('should decrypt with correct password', async () => {
    const encrypted = await encryptWithPassword('data', 'password');
    const decrypted = await decryptWithPassword(encrypted, 'password');
    expect(decrypted).toBe('data');
  });
  
  // Error path - wrong password
  it('should fail with wrong password', async () => {
    const encrypted = await encryptWithPassword('data', 'password1');
    await expect(decryptWithPassword(encrypted, 'password2'))
      .rejects.toThrow('Decryption failed');
  });
  
  // Edge case - empty password
  it('should reject empty password', async () => {
    await expect(encryptWithPassword('data', ''))
      .rejects.toThrow('Password required');
  });
  
  // Edge case - large data
  it('should handle large data', async () => {
    const largeData = 'x'.repeat(100_000);
    const encrypted = await encryptWithPassword(largeData, 'password');
    const decrypted = await decryptWithPassword(encrypted, 'password');
    expect(decrypted).toBe(largeData);
  });
});
```

## Integration Tests

### ? Test API Endpoints
```typescript
describe('POST /api/pastes', () => {
  let server: Server;
  
  beforeAll(async () => {
    server = await startTestServer();
  });
  
  afterAll(async () => {
    await server.close();
  });
  
  it('should create paste with valid data', async () => {
    const response = await request(server)
      .post('/api/pastes')
      .send({
        ct: 'dGVzdA==',  // base64 'test'
        iv: 'MTIzNDU2Nzg5MDEyMzQ1Ng==',  // base64 16 bytes
        meta: {}
      })
      .expect(200);
    
    expect(response.body).toHaveProperty('id');
    expect(response.body).toHaveProperty('deleteToken');
  });
  
  it('should reject invalid base64 ciphertext', async () => {
    const response = await request(server)
      .post('/api/pastes')
      .send({
        ct: 'not-valid-base64!!!',
        iv: 'MTIzNDU2Nzg5MDEyMzQ1Ng==',
        meta: {}
      })
      .expect(400);
    
    expect(response.body.error).toContain('Invalid');
  });
  
  it('should enforce rate limiting', async () => {
    // Make requests until rate limited
    const requests = Array(15).fill(null).map(() =>
      request(server)
        .post('/api/pastes')
        .send({ ct: 'dGVzdA==', iv: 'MTIzNDU2Nzg5MDEyMzQ1Ng==', meta: {} })
    );
    
    const responses = await Promise.all(requests);
    const rateLimited = responses.some(r => r.status === 429);
    
    expect(rateLimited).toBe(true);
  });
});
```

## E2E Tests

### ? Test User Flows
```typescript
describe('Create and View Paste', () => {
  let browser: Browser;
  let page: Page;
  
  beforeAll(async () => {
    browser = await chromium.launch();
  });
  
  afterAll(async () => {
    await browser.close();
  });
  
  beforeEach(async () => {
    page = await browser.newPage();
  });
  
  afterEach(async () => {
    await page.close();
  });
  
  it('should create and view encrypted paste', async () => {
    // Navigate to home page
    await page.goto('http://localhost:8080');
    
    // Enter text
    await page.fill('#paste-text', 'Secret message');
    
    // Submit form
    await page.click('#submit-button');
    
    // Wait for URL to appear
    await page.waitForSelector('#share-url');
    const shareUrl = await page.inputValue('#share-url');
    
    // Verify URL has fragment (key)
    expect(shareUrl).toMatch(/#[A-Za-z0-9+/=]+$/);
    
    // Navigate to view page
    await page.goto(shareUrl);
    
    // Wait for decryption
    await page.waitForSelector('#paste-content');
    
    // Verify content matches
    const content = await page.textContent('#paste-content');
    expect(content).toBe('Secret message');
  });
  
  it('should show error for invalid paste ID', async () => {
    await page.goto('http://localhost:8080/view.html#invalid:AAAA');
    
    await page.waitForSelector('.error-message');
    const error = await page.textContent('.error-message');
    
    expect(error).toContain('not found');
  });
});
```

## Performance Tests

### ? Test Performance Requirements
```typescript
describe('Encryption Performance', () => {
  it('should encrypt 1MB in under 500ms', async () => {
    const key = await generateKey();
    const largeText = 'x'.repeat(1_000_000);
    
    const startTime = Date.now();
    await encryptPaste(largeText, key);
    const duration = Date.now() - startTime;
    
    expect(duration).toBeLessThan(500);
  });
});
```

## Security Tests

### ? Test Security Requirements
```typescript
describe('Security', () => {
  it('should never send encryption key to server', async () => {
    const mockFetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'test', deleteToken: 'token' })
    });
    global.fetch = mockFetch as any;
    
    await createPaste('secret text');
    
    // Check all fetch calls
    const calls = mockFetch.mock.calls;
    for (const [url, options] of calls) {
      const body = JSON.parse(options?.body || '{}');
      expect(body).not.toHaveProperty('key');
      expect(body).not.toHaveProperty('password');
    }
  });
  
  it('should use different IVs for each encryption', async () => {
    const key = await generateKey();
    const ivs = new Set<string>();
    
    for (let i = 0; i < 100; i++) {
      const { iv } = await encryptPaste('test', key);
      ivs.add(iv);
    }
    
    // All IVs should be unique
    expect(ivs.size).toBe(100);
  });
  
  it('should not log sensitive data', async () => {
    const consoleSpy = jest.spyOn(console, 'log');
    const consoleErrorSpy = jest.spyOn(console, 'error');
    
    const key = await generateKey();
    await encryptPaste('secret', key);
    
    // Check no logs contain 'secret'
    const allLogs = [
      ...consoleSpy.mock.calls,
      ...consoleErrorSpy.mock.calls
    ].flat().join(' ');
    
    expect(allLogs).not.toContain('secret');
    
    consoleSpy.mockRestore();
    consoleErrorSpy.mockRestore();
  });
});
```

## Test Utilities

### ? Create Helper Functions
```typescript
// test-utils.ts
export async function createTestPaste(
  overrides?: Partial<Paste>
): Promise<Paste> {
  const key = await generateKey();
  const { ct, iv } = await encryptPaste('test', key);
  
  return {
    id: 'test-id',
    ct,
    iv,
    meta: {},
    ...overrides
  };
}

export function mockFetchSuccess(data: any): void {
  global.fetch = jest.fn().mockResolvedValue({
    ok: true,
    json: async () => data
  }) as any;
}

export function mockFetchError(status: number, message: string): void {
  global.fetch = jest.fn().mockResolvedValue({
    ok: false,
    status,
    json: async () => ({ error: message })
  }) as any;
}
```

## Coverage Requirements


- **Minimum**: 85% overall
- **Critical functions**: 100% (encryption, authentication, validation)
- **Error paths**: Must be tested
- **Edge cases**: Document and test

## Remember


- **One assertion per test** (when possible)
- **Test behavior, not implementation**
- **Use descriptive test names** (it should...)
- **Arrange-Act-Assert** pattern
- **Mock external dependencies**
- **Test error paths** as much as happy paths
- **Keep tests fast** (< 100ms for unit tests)
- **Clean up after tests** (no side effects)
