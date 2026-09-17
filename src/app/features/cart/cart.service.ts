import { afterNextRender, computed, Injectable, signal } from '@angular/core';
import { EmbroideryPattern } from '../products/product.model';
import { demoProducts } from '../products/products.data';
import {
  garmentColors,
  garmentSizes,
  dragonEmbroideryOptions,
  sweatshirtPricesInGrosz,
} from '../products/embroidered-product-options';
import { SweatshirtCartItem, SweatshirtConfiguration } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';
import { readStoredSweatshirts } from './cart-storage';

@Injectable({
  providedIn: 'root',
})
export class CartService {
  private readonly storageKey = 'lilimi.cart.v1';
  private readonly sweatshirtStorageKey = 'lilimi.cart.sweatshirts.v1';
  private readonly patternsState = signal<readonly EmbroideryPattern[]>([]);

  private readonly sweatshirtsState = signal<readonly SweatshirtCartItem[]>([]);

  private storageReady = false;

  private readonly readyState = signal(false);
  readonly isReady = this.readyState.asReadonly();

  readonly patterns = this.patternsState.asReadonly();

  readonly sweatshirts = this.sweatshirtsState.asReadonly();

  readonly itemCount = computed(
    () =>
      this.patterns().length + this.sweatshirts().reduce((total, item) => total + item.quantity, 0),
  );

  readonly subtotalInGrosz = computed(() => {
    const patternsSubtotal = this.patterns().reduce(
      (total, product) => total + product.priceInGrosz,
      0,
    );

    const sweatshirtsSubtotal = this.sweatshirts().reduce(
      (total, item) => total + this.getSweatshirtUnitPrice(item) * item.quantity,
      0,
    );

    return patternsSubtotal + sweatshirtsSubtotal;
  });

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

      const savedSweatshirts = this.loadSweatshirts();

      this.sweatshirtsState.update((currentItems) =>
        readStoredSweatshirts([...savedSweatshirts, ...currentItems]),
      );

      this.storageReady = true;
      this.readyState.set(true);
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
    this.sweatshirtsState.set([]);
    this.save();
  }

  getSweatshirtUnitPrice(item: SweatshirtCartItem): number {
    return sweatshirtPricesInGrosz[item.configuration.fit][item.configuration.embroideryOptionId];
  }

  addSweatshirt(configuration: SweatshirtConfiguration): boolean {
    const validSize = garmentSizes[configuration.fit]?.includes(configuration.size);

    const validColor = garmentColors.includes(configuration.color);

    const validEmbroidery = dragonEmbroideryOptions.some(
      (option) => option.id === configuration.embroideryOptionId,
    );

    if (!validSize || !validColor || !validEmbroidery) {
      return false;
    }

    const newItem: SweatshirtCartItem = {
      kind: 'sweatshirt',
      productId: 'embroidered-002',
      patternId: 'pattern-001',
      configuration: { ...configuration },
      quantity: 1,
    };

    const key = getCartItemKey(newItem);

    this.sweatshirtsState.update((items) => {
      const alreadyAdded = items.some((item) => getCartItemKey(item) === key);

      if (!alreadyAdded) {
        return [...items, newItem];
      }

      return items.map((item) =>
        getCartItemKey(item) === key ? { ...item, quantity: item.quantity + 1 } : item,
      );
    });

    this.save();

    return true;
  }

  removeSweatshirt(key: string): void {
    this.sweatshirtsState.update((items) => items.filter((item) => getCartItemKey(item) !== key));

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
      localStorage.setItem(this.sweatshirtStorageKey, JSON.stringify(this.sweatshirts()));
    } catch {
      // The in-memory cart remains usable if persistence fails.
    }
  }

  private loadSweatshirts(): SweatshirtCartItem[] {
    try {
      const stored = localStorage.getItem(this.sweatshirtStorageKey);

      if (stored === null) {
        return [];
      }

      const parsed: unknown = JSON.parse(stored);

      return readStoredSweatshirts(parsed);
    } catch {
      // Keep the cart usable when stored data cannot be read.
      return [];
    }
  }

  updateSweatshirtQuantity(key: string, quantity: number): void {
    if (!Number.isSafeInteger(quantity) || quantity < 1) {
      return;
    }

    this.sweatshirtsState.update((items) =>
      items.map((item) => (getCartItemKey(item) === key ? { ...item, quantity } : item)),
    );

    this.save();
  }
}
