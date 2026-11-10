import { afterNextRender, computed, inject, Injectable, signal, effect, untracked } from '@angular/core';

import { EmbroideredProduct, EmbroideryPattern, Product } from '../products/product.model';
import {
  garmentColors,
  garmentSizes,
  isEmbroideryOptionId,
} from '../products/embroidered-product-options';
import {
  MAX_CART_ITEM_QUANTITY,
  PhysicalCartItem,
  SweatshirtCartItem,
  SweatshirtConfiguration,
} from './cart-item.model';
import { getCartItemKey } from './cart-item-key';
import { readStoredPhysicalItems, readStoredSweatshirts } from './cart-storage';
import { SweatshirtVariantsService } from '../products/sweatshirt-variants.service';
import { ProductCatalogService } from '../products/product-catalog.service';

@Injectable({
  providedIn: 'root',
})
export class CartService {
  private readonly catalog = inject(ProductCatalogService);
  private readonly variantsService = inject(SweatshirtVariantsService);
  private readonly storageKey = 'lilimi.cart.v1';
  private readonly physicalStorageKey = 'lilimi.cart.physical.v1';
  private readonly physicalState = signal<readonly PhysicalCartItem[]>([]);
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
  readonly physicalItems = this.physicalState.asReadonly();
  readonly requiresShipping = computed(() => this.sweatshirts().length + this.physicalItems().length > 0);
  readonly physicalLines = computed(() => this.physicalItems().map((item) => {
    const product = this.catalog.productsById().get(item.productId);
    return {
      key: getCartItemKey(item),
      item,
      product,
      options: Object.entries(item.options),
      unitPriceInGrosz: this.matchesPhysicalOptions(product, item.options) ? product.priceInGrosz : null,
    };
  }));

  private readonly sweatshirtSlugs = computed(() => {
    const slugs = new Set<string>();

    for (const item of this.sweatshirts()) {
      const slug = this.getSweatshirtSlug(item.productId);

      if (slug !== undefined) {
        slugs.add(slug);
      }
    }

    return slugs;
  });

  readonly sweatshirtLines = computed(() =>
    this.sweatshirts().map((item) => {
      const variant = this.findSweatshirtVariant(item);

      return {
        key: getCartItemKey(item),
        item,
        product: this.catalog.productsById().get(item.productId),
        pattern: this.catalog.productsById().get(item.patternId),
        variantId: variant?.id,
        embroidery: variant
          ? {
              id: variant.embroideryOptionId,
              widthMm: variant.widthMm,
              heightMm: variant.heightMm,
              placement: variant.placement,
            }
          : undefined,
        unitPriceInGrosz: variant?.priceInGrosz ?? null,
      };
    }),
  );

  readonly itemCount = computed(
    () =>
      this.patternIdsState().length +
      this.sweatshirts().reduce((total, item) => total + item.quantity, 0) +
      this.physicalItems().reduce((total, item) => total + item.quantity, 0),
  );

  readonly subtotalInGrosz = computed<number | null>(() => {
    if (this.pricingStatus() !== 'ready') {
      return null;
    }
    let total = this.patterns().reduce((sum, product) => sum + product.priceInGrosz, 0);

    for (const { item, unitPriceInGrosz: price } of [...this.sweatshirtLines(), ...this.physicalLines()]) {
      if (price === null) {
        return null;
      }

      total += price * item.quantity;
    }

    return total;
  });

  readonly pricingStatus = computed<'ready' | 'loading' | 'error' | 'unavailable'>(() => {
    const needsCatalog = this.patternIdsState().length > 0 || this.requiresShipping();
    const catalogStatus = needsCatalog ? this.catalog.state().status : 'ready';
    const variantsStatuses = [...this.sweatshirtSlugs()].map(
      (slug) => this.variantsService.findSource(slug)?.state().status ?? 'loading',
    );

    if (catalogStatus === 'error' || variantsStatuses.includes('error')) {
      return 'error';
    }

    if (catalogStatus === 'loading' || variantsStatuses.includes('loading')) {
      return 'loading';
    }

    const unavailableSweatshirt = this.sweatshirts().some(
      (item) =>
        !this.catalog.productsById().has(item.productId) ||
        !this.catalog.productsById().has(item.patternId) ||
        this.findSweatshirtVariant(item) === undefined,
    );

    return this.unavailablePatternIds().length > 0 || unavailableSweatshirt ||
      this.physicalLines().some((line) => line.unitPriceInGrosz === null)
      ? 'unavailable'
      : 'ready';
  });

  reloadPrices(): void {
    const slugs = this.sweatshirtSlugs();

    if (this.patternIdsState().length > 0 || this.requiresShipping()) {
      this.catalog.reload();
    }

    for (const slug of slugs) {
      this.variantsService.findSource(slug)?.reload();
    }
  }

  constructor() {
    effect(() => {
      const slugs = this.sweatshirtSlugs();

      untracked(() => {
        for (const slug of slugs) {
          this.variantsService.forProduct(slug);
        }
      });
    });

    afterNextRender(() => {
      const savedPatternIds = this.readStoredPatternIds();

      this.patternIdsState.update((currentIds) => [
        ...new Set([...savedPatternIds, ...currentIds]),
      ]);

      const savedSweatshirts = this.loadSweatshirts();

      this.sweatshirtsState.update((currentItems) =>
        readStoredSweatshirts([...savedSweatshirts, ...currentItems]),
      );

      this.physicalState.update((items) => {
        try {
          const stored = readStoredPhysicalItems(JSON.parse(localStorage.getItem(this.physicalStorageKey) ?? '[]'));
          return readStoredPhysicalItems([...stored, ...items]);
        } catch {
          return items;
        }
      });

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
    this.physicalState.set([]);
    this.save();
  }

  addPhysical(product: EmbroideredProduct, options: Readonly<Record<string, string>>): boolean {
    if (!this.matchesPhysicalOptions(product, options)) {
      return false;
    }

    const item: PhysicalCartItem = { kind: 'physical', productId: product.id, options: { ...options }, quantity: 1 };
    const key = getCartItemKey(item);
    const existing = this.physicalItems().find((line) => getCartItemKey(line) === key);

    if (existing?.quantity === MAX_CART_ITEM_QUANTITY) {
      return false;
    }

    this.physicalState.update((items) => readStoredPhysicalItems([...items, item]));
    this.save();
    return true;
  }

  removePhysical(key: string): void {
    this.physicalState.update((items) => items.filter((item) => getCartItemKey(item) !== key));
    this.save();
  }

  updatePhysicalQuantity(key: string, quantity: number): void {
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > MAX_CART_ITEM_QUANTITY) {
      return;
    }

    this.physicalState.update((items) =>
      items.map((item) => getCartItemKey(item) === key ? { ...item, quantity } : item),
    );
    this.save();
  }

  private matchesPhysicalOptions(
    product: Product | undefined, options: Readonly<Record<string, string>>,
  ): product is EmbroideredProduct {
    if (product?.category !== 'embroidered-products' || product.productType !== 'physical') {
      return false;
    }

    const attributes = product.variantAttributes ?? [];
    return Object.keys(options).length === attributes.length &&
      attributes.every((attribute) => attribute.values.includes(options[attribute.name]));
  }

  addSweatshirt(
    productId: string,
    patternId: string,
    configuration: SweatshirtConfiguration,
  ): boolean {
    const validSize = garmentSizes[configuration.fit]?.includes(configuration.size);

    const validColor = garmentColors.includes(configuration.color);

    const validEmbroidery = isEmbroideryOptionId(configuration.embroideryOptionId);

    if (!validSize || !validColor || !validEmbroidery) {
      return false;
    }

    const newItem: SweatshirtCartItem = {
      kind: 'sweatshirt',
      productId,
      patternId,
      configuration: { ...configuration },
      quantity: 1,
    };

    const slug = this.getSweatshirtSlug(productId);

    if (slug === undefined) {
      return false;
    }

    this.variantsService.forProduct(slug);

    if (this.findSweatshirtVariant(newItem) === undefined) {
      return false;
    }

    const key = getCartItemKey(newItem);

    const existingItem = this.sweatshirtsState().find((item) => getCartItemKey(item) === key);

    if (existingItem && existingItem.quantity >= MAX_CART_ITEM_QUANTITY) {
      return false;
    }

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
      localStorage.setItem(this.physicalStorageKey, JSON.stringify(this.physicalItems()));
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
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > MAX_CART_ITEM_QUANTITY) {
      return;
    }

    this.sweatshirtsState.update((items) =>
      items.map((item) => (getCartItemKey(item) === key ? { ...item, quantity } : item)),
    );

    this.save();
  }

  private getSweatshirtSlug(productId: string): string | undefined {
    const product = this.catalog.productsById().get(productId);

    return product?.category === 'embroidered-products' && product.productType === 'sweatshirt'
      ? product.slug
      : undefined;
  }

  private findSweatshirtVariant(item: SweatshirtCartItem) {
    const slug = this.getSweatshirtSlug(item.productId);
    const source = slug === undefined ? undefined : this.variantsService.findSource(slug);

    return source
      ?.variants()
      .find(
        (variant) =>
          variant.patternId === item.patternId &&
          variant.fit === item.configuration.fit &&
          variant.size === item.configuration.size &&
          variant.color === item.configuration.color &&
          variant.embroideryOptionId === item.configuration.embroideryOptionId,
      );
  }
}
