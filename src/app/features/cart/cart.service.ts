import { afterNextRender, computed, Injectable, signal } from '@angular/core';
import { EmbroideryPattern } from '../products/product.model';
import { demoProducts } from '../products/products.data';

@Injectable({
  providedIn: 'root',
})
export class CartService {
  private readonly storageKey = 'lilimi.cart.v1';
  private readonly patternsState = signal<readonly EmbroideryPattern[]>([]);
  private storageReady = false;

  readonly patterns = this.patternsState.asReadonly();

  readonly itemCount = computed(() => this.patterns().length);

  readonly subtotalInGrosz = computed(() =>
    this.patterns().reduce((total, product) => total + product.priceInGrosz, 0),
  );

  constructor() {
    afterNextRender(() => {
      const savedPatterns = this.readStoredPatterns();

      this.patternsState.update((currentPatterns) => {
        const merged = new Map(savedPatterns.map((product) => [product.id, product]));

        for (const product of currentPatterns) {
          merged.set(product.id, product);
        }

        return [...merged.values()];
      });

      this.storageReady = true;
      this.save();
    });
  }

  addPattern(product: EmbroideryPattern): void {
    if (product.priceType !== 'fixed') {
      return;
    }

    this.patternsState.update((patterns) => {
      const alreadyAdded = patterns.some((item) => item.id === product.id);

      return alreadyAdded ? patterns : [...patterns, product];
    });

    this.save();
  }

  removeProduct(productId: string): void {
    this.patternsState.update((patterns) => patterns.filter((product) => product.id !== productId));

    this.save();
  }

  clear(): void {
    this.patternsState.set([]);
    this.save();
  }

  private readStoredPatterns(): EmbroideryPattern[] {
    try {
      const stored = localStorage.getItem(this.storageKey);

      if (stored === null) {
        return [];
      }

      const parsed: unknown = JSON.parse(stored);

      if (!Array.isArray(parsed)) {
        return [];
      }

      const ids = new Set(parsed.filter((id): id is string => typeof id === 'string'));

      return [...ids].flatMap((id) => {
        const product = demoProducts.find((item) => item.id === id);

        return product?.category === 'embroidery-patterns' && product.priceType === 'fixed'
          ? [product]
          : [];
      });
    } catch {
      // Keep the cart usable when storage is unavailable or malformed.
      return [];
    }
  }

  private save(): void {
    if (!this.storageReady) {
      return;
    }

    try {
      const ids = this.patterns().map((product) => product.id);

      localStorage.setItem(this.storageKey, JSON.stringify(ids));
    } catch {
      // The in-memory cart remains usable if persistence fails.
    }
  }
}
