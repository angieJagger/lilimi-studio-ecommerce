import type { EmbroideryOptionId, GarmentColor, GarmentFit } from './embroidered-product-options';

export interface GarmentVariantApiResponse {
  readonly id: string;
  readonly patternId: string;
  readonly fit: GarmentFit;
  readonly size: string;
  readonly color: GarmentColor;
  readonly embroideryOptionId: EmbroideryOptionId;
  readonly widthMm: number;
  readonly heightMm: number;
  readonly placement: 'chest' | 'back';
  readonly priceInGrosz: number;
  readonly currency: 'PLN';
}
