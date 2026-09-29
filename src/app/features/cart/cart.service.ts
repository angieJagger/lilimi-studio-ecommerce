import { afterNextRender, computed, inject, Injectable, signal } from '@angular/core';

import { EmbroideryPattern } from '../products/product.model';
import {
  garmentColors,
  garmentSizes,
  dragonEmbroideryOptions
} from '../products/embroidered-product-options';
import { SweatshirtCartItem, SweatshirtConfiguration } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';
import { readStoredSweatshirts } from './cart-storage';
import { SweatshirtVariantsService } from '../products/sweatshirt-variants.service';
import { ProductCatalogService } from '../products/product-catalog.service';

@Injectable({
  providedIn: 'root',
})
export class CartService {
  private readonly catalog = inject(ProductCatalogService);
  private readonly variantsService = inject(SweatshirtVariantsService);
  private readonly storageKey = 'lilimi.cart.v1';
  private readonly sweatshirtStorageKey = 'lilimi.cart.sweatshirts.v1';
  private readonly patternIdsState = signal<readonly string[]>([]);

  private readonly sweatshirtsState = signal<readonly SweatshirtCartItem[]>([]);

  private storageReady = false;

  private readonly readyState = signal(false);
  readonly isReady = this.readyState.asReadonly();

  readonly patterns = computed(() =>
    this.patternIdsState().flatMap((id) => {
      const product = this.catalog.productsById().get(id);

      return product?.category === 'embroidery-patterns' && product.priceType === 'fixed'
        ? [product]
        : [];
    }),
  );

  readonly unavailablePatternIds = computed(() => {
    if (this.catalog.state().status !== 'ready') {
      return [];
    }

    const availableIds = new Set(this.patterns().map((product) => product.id));

    return this.patternIdsState().filter((id) => !availableIds.has(id));
  });

  readonly sweatshirts = this.sweatshirtsState.asReadonly();

  readonly sweatshirtLines = computed(() =>
    this.sweatshirts().map((item) => ({
      key: getCartItemKey(item),
      item,
      product: this.catalog.productsById().get(item.productId),
      pattern: this.catalog.productsById().get(item.patternId),
      embroidery: dragonEmbroideryOptions.find(
        (option) => option.id === item.configuration.embroideryOptionId,
      ),
      unitPriceInGrosz: this.getSweatshirtUnitPrice(item),
    })),
  );

  readonly itemCount = computed(
    () =>
      this.patternIdsState().length +
      this.sweatshirts().reduce((total, item) => total + item.quantity, 0),
  );

  readonly subtotalInGrosz = computed<number | null>(() => {
    if (this.pricingStatus() !== 'ready') {
      return null;
    }
    let total = this.patterns().reduce((sum, product) => sum + product.priceInGrosz, 0);

    for (const item of this.sweatshirts()) {
      const price = this.getSweatshirtUnitPrice(item);

      if (price === null) {
        return null;
      }

      total += price * item.quantity;
    }

    return total;
  });

  readonly pricingStatus = computed<'ready' | 'loading' | 'error' | 'unavailable'>(() => {
    const needsCatalog = this.patternIdsState().length > 0 || this.sweatshirts().length > 0;
    const needsVariants = this.sweatshirts().length > 0;

    const catalogStatus = needsCatalog ? this.catalog.state().status : 'ready';

    const variantsStatus = needsVariants ? this.variantsService.state().status : 'ready';

    if (catalogStatus === 'error' || variantsStatus === 'error') {
      return 'error';
    }

    if (catalogStatus === 'loading' || variantsStatus === 'loading') {
      return 'loading';
    }

        const unavailableSweatshirt = this.sweatshirts().some(
          (item) =>
            !this.catalog.productsById().has(item.productId) ||
            !this.catalog.productsById().has(item.patternId) ||
            this.getSweatshirtUnitPrice(item) === null,
        );

    return this.unavailablePatternIds().length > 0 || unavailableSweatshirt
      ? 'unavailable'
      : 'ready';
  });

  reloadPrices(): void {
    if (this.patternIdsState().length > 0 || this.sweatshirts().length > 0) {
      this.catalog.reload();
    }

    if (this.sweatshirts().length > 0) {
      this.variantsService.reload();
    }
  }

  constructor() {
    afterNextRender(() => {
      const savedPatternIds = this.readStoredPatternIds();

      this.patternIdsState.update((currentIds) => [
        ...new Set([...savedPatternIds, ...currentIds]),
      ]);

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

    this.patternIdsState.update((ids) => (ids.includes(product.id) ? ids : [...ids, product.id]));

    this.save();
  }

  removeProduct(productId: string): void {
    this.patternIdsState.update((ids) => ids.filter((id) => id !== productId));

    this.save();
  }

  clear(): void {
    this.patternIdsState.set([]);
    this.sweatshirtsState.set([]);
    this.save();
  }

  getSweatshirtUnitPrice(item: SweatshirtCartItem): number | null {
    if (item.productId !== 'embroidered-002') {
      return null;
    }

    const variant = this.variantsService
      .variants()
      .find(
        (variant) =>
          variant.patternId === item.patternId &&
          variant.fit === item.configuration.fit &&
          variant.size === item.configuration.size &&
          variant.color === item.configuration.color &&
          variant.embroideryOptionId === item.configuration.embroideryOptionId,
      );

    return variant?.priceInGrosz ?? null;
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

    if (this.getSweatshirtUnitPrice(newItem) === null) {
      return false;
    }

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

  private readStoredPatternIds(): string[] {
    try {
      const stored = localStorage.getItem(this.storageKey);

      if (stored === null) {
        return [];
      }

      const parsed: unknown = JSON.parse(stored);

      if (!Array.isArray(parsed)) {
        return [];
      }

      return [
        ...new Set(
          parsed.filter((id): id is string => typeof id === 'string' && id.trim().length > 0),
        ),
      ];
    } catch {
      return [];
    }
  }

  private save(): void {
    if (!this.storageReady) {
      return;
    }

    try {
      const ids = this.patternIdsState();

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
