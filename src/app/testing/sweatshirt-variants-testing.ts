import { computed, signal } from '@angular/core';
import type { Provider } from '@angular/core';
import { vi } from 'vitest';
import { SweatshirtVariantsService } from '../features/products/sweatshirt-variants.service';
import type { GarmentVariantApiResponse } from '../features/products/garment-variant-api.model';

const baseVariant: GarmentVariantApiResponse = {
  id: 'test-women-m-black-small',
  patternId: 'pattern-001',
  fit: 'women',
  size: 'M',
  color: 'black',
  embroideryOptionId: 'small-front',
  widthMm: 100,
  heightMm: 100,
  placement: 'chest',
  priceInGrosz: 14900,
  currency: 'PLN',
};

export const testSweatshirtVariants: readonly GarmentVariantApiResponse[] = [
  baseVariant,
  {
    ...baseVariant,
    id: 'test-women-l-black-small',
    size: 'L',
  },
  {
    ...baseVariant,
    id: 'test-children-92-black-large',
    fit: 'children',
    size: '92',
    embroideryOptionId: 'large-back',
    widthMm: 200,
    heightMm: 200,
    placement: 'back',
  },
  {
    ...baseVariant,
    id: 'test-children-92-navy-large',
    fit: 'children',
    size: '92',
    color: 'navy',
    embroideryOptionId: 'large-back',
    widthMm: 200,
    heightMm: 200,
    placement: 'back',
  },
];

type TestVariantsState =
  | { status: 'loading' }
  | { status: 'error' }
  | {
      status: 'ready';
      variants: readonly GarmentVariantApiResponse[];
    };

export function createSweatshirtVariantsMock() {
  const state = signal<TestVariantsState>({
    status: 'ready',
    variants: testSweatshirtVariants,
  });

  return {
    state,
    variants: computed(() => {
      const current = state();

      return current.status === 'ready' ? current.variants : [];
    }),
    reload: vi.fn(),
  };
}

export function provideSweatshirtVariantsTesting(): Provider {
  return {
    provide: SweatshirtVariantsService,
    useFactory: createSweatshirtVariantsMock,
  };
}
