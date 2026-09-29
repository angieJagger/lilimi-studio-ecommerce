import { computed, signal } from '@angular/core';
import type { Provider } from '@angular/core';
import { vi } from 'vitest';

import { ProductCatalogService } from '../features/products/product-catalog.service';
import type { EmbroideryPattern, Product } from '../features/products/product.model';

const pattern: EmbroideryPattern = {
  id: 'test-pattern',
  slug: 'test-pattern',
  category: 'embroidery-patterns',
  name: {
    pl: 'Wzór testowy',
    en: 'Test pattern',
  },
  description: {
    pl: 'Opis testowy',
    en: 'Test description',
  },
  priceInGrosz: 2900,
  priceType: 'fixed',
  fileFormats: ['DST'],
};

export const testCatalogProducts: readonly Product[] = [
  pattern,
  {
    ...pattern,
    id: 'second-test-pattern',
    slug: 'second-test-pattern',
    priceInGrosz: 1900,
  },
  {
    ...pattern,
    id: 'pattern-001',
    slug: 'forest-dragon',
  },
  {
    ...pattern,
    id: 'pattern-002',
    slug: 'floral-monogram',
    priceInGrosz: 1900,
  },
  {
    id: 'embroidered-002',
    slug: 'embroidered-sweatshirt',
    category: 'embroidered-products',
    name: {
      pl: 'Bluza z haftem',
      en: 'Embroidered sweatshirt',
    },
    description: {
      pl: 'Bluza testowa.',
      en: 'Test sweatshirt.',
    },
    priceInGrosz: 12900,
    priceType: 'from',
    madeToOrder: true,
    personalizationAvailable: true,
  },
];

type TestCatalogState =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; products: readonly Product[] };

export function createProductCatalogMock() {
  const state = signal<TestCatalogState>({
    status: 'ready',
    products: testCatalogProducts,
  });

  const products = computed(() => {
    const current = state();

    return current.status === 'ready' ? current.products : [];
  });

  const productsById = computed(() => new Map(products().map((product) => [product.id, product])));

  return {
    state,
    products,
    productsById,
    reload: vi.fn(),
  };
}

export function provideProductCatalogTesting(): Provider {
  return {
    provide: ProductCatalogService,
    useFactory: createProductCatalogMock,
  };
}
