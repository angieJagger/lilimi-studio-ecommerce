import { Injectable } from '@angular/core';
import type { CreateOrderRequest } from './order.model';

interface PendingSubmission {
  readonly key: string;
  readonly signature: string;
  readonly completed: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class OrderSubmissionKeyService {
  private readonly storageKey = 'lilimi.pending-order-submission';
  private pending: PendingSubmission | null = null;

  async getKey(request: CreateOrderRequest): Promise<string> {
    const signature = await this.hash(request);

    const existing = this.pending ?? this.readStored();

    if (existing?.signature === signature && !existing.completed) {
      this.pending = existing;
      return existing.key;
    }

    this.pending = {
      key: crypto.randomUUID(),
      signature,
      completed: false,
    };

    this.writeStored(this.pending);

    return this.pending.key;
  }

  markCompleted(key: string): void {
    if (this.pending?.key !== key) {
      return;
    }

    this.pending = {
      ...this.pending,
      completed: true,
    };

    this.writeStored(this.pending);
  }

  private async hash(request: CreateOrderRequest): Promise<string> {
    const bytes = new TextEncoder().encode(JSON.stringify(request));
    const digest = await crypto.subtle.digest('SHA-256', bytes);

    return Array.from(new Uint8Array(digest))
      .map((value) => value.toString(16).padStart(2, '0'))
      .join('');
  }

  private readStored(): PendingSubmission | null {
    try {
      const stored = sessionStorage.getItem(this.storageKey);

      if (!stored) {
        return null;
      }

      const value = JSON.parse(stored);

      if (
        typeof value?.key !== 'string' ||
        typeof value?.signature !== 'string' ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.key) ||
        !/^[0-9a-f]{64}$/.test(value.signature)
      ) {
        return null;
      }

      return {
        key: value.key,
        signature: value.signature,
        completed: value.completed === true,
      };
    } catch {
      return null;
    }
  }

  private writeStored(value: PendingSubmission): void {
    try {
      sessionStorage.setItem(this.storageKey, JSON.stringify(value));
    } catch {
      // The in-memory key still supports retries in this page.
    }
  }
}
