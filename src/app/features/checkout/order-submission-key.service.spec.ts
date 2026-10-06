import { vi } from 'vitest';
import { OrderSubmissionKeyService } from './order-submission-key.service';
import type { CreateOrderRequest } from './order.model';

describe('OrderSubmissionKeyService', () => {
  const firstKey = '7c82dd0f-9289-4a9e-bf58-05b415d6da48';
  const secondKey = '43c5fc12-5ed2-49d0-bbaa-dc237a908c93';

  const request: CreateOrderRequest = {
    language: 'pl',
    contact: {
      fullName: 'Test Customer',
      email: 'customer@example.com',
    },
    delivery: {
      kind: 'digital',
    },
    items: [
      {
        kind: 'digital',
        productId: 'pattern-001',
        quantity: 1,
      },
    ],
  };

  let storage: Map<string, string>;

  const digest = vi.fn();
  const randomUUID = vi.fn();
  const getItem = vi.fn();
  const setItem = vi.fn();

  beforeEach(() => {
    storage = new Map();

    digest.mockReset();
    digest.mockResolvedValue(new Uint8Array(32).fill(1).buffer);

    randomUUID.mockReset();
    randomUUID.mockReturnValueOnce(firstKey).mockReturnValue(secondKey);

    getItem.mockReset();
    getItem.mockImplementation((key: string) => storage.get(key) ?? null);

    setItem.mockReset();
    setItem.mockImplementation((key: string, value: string) => {
      storage.set(key, value);
    });

    vi.stubGlobal('crypto', {
      randomUUID,
      subtle: { digest },
    });

    vi.stubGlobal('sessionStorage', {
      getItem,
      setItem,
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should reuse a key for the same request', async () => {
    const service = new OrderSubmissionKeyService();

    const first = await service.getKey(request);
    const second = await service.getKey(request);

    expect(first).toBe(firstKey);
    expect(second).toBe(first);
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('should recover the key in a new service instance', async () => {
    const firstService = new OrderSubmissionKeyService();
    const first = await firstService.getKey(request);

    const restoredService = new OrderSubmissionKeyService();
    const restored = await restoredService.getKey(request);

    expect(restored).toBe(first);
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('should create a new key when the request signature changes', async () => {
    digest
      .mockResolvedValueOnce(new Uint8Array(32).fill(1).buffer)
      .mockResolvedValueOnce(new Uint8Array(32).fill(2).buffer);

    const service = new OrderSubmissionKeyService();

    const first = await service.getKey(request);

    const second = await service.getKey({
      ...request,
      contact: {
        ...request.contact,
        email: 'different@example.com',
      },
    });

    expect(first).toBe(firstKey);
    expect(second).toBe(secondKey);
    expect(second).not.toBe(first);
  });

  it('should ignore malformed stored data', async () => {
    storage.set('lilimi.pending-order-submission', '{broken-json');

    const service = new OrderSubmissionKeyService();

    expect(await service.getKey(request)).toBe(firstKey);
  });

  it('should preserve the in-memory key when storage is blocked', async () => {
    getItem.mockImplementation(() => {
      throw new Error('Storage blocked');
    });

    setItem.mockImplementation(() => {
      throw new Error('Storage blocked');
    });

    const service = new OrderSubmissionKeyService();

    const first = await service.getKey(request);
    const second = await service.getKey(request);

    expect(second).toBe(first);
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('should create a new key after a confirmed order', async () => {
    const service = new OrderSubmissionKeyService();

    const first = await service.getKey(request);
    service.markCompleted(first);

    const next = await service.getKey(request);

    expect(next).toBe(secondKey);
    expect(next).not.toBe(first);
  });

  it('should remember completion in a new service instance', async () => {
    const firstService = new OrderSubmissionKeyService();

    const first = await firstService.getKey(request);
    firstService.markCompleted(first);

    const restoredService = new OrderSubmissionKeyService();
    const next = await restoredService.getKey(request);

    expect(next).toBe(secondKey);
    expect(next).not.toBe(first);
  });
});
