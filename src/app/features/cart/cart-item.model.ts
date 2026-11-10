import {
  EmbroideryOptionId,
  GarmentColor,
  GarmentFit,
} from '../products/embroidered-product-options';

// Quantities are stored as signed 32-bit integers by the order API.
export const MAX_CART_ITEM_QUANTITY = 2_147_483_647;

export interface DigitalCartItem {
  readonly kind: 'digital';
  readonly productId: string;
  readonly quantity: 1;
}

export interface SweatshirtConfiguration {
  readonly fit: GarmentFit;
  readonly size: string;
  readonly color: GarmentColor;
  readonly embroideryOptionId: EmbroideryOptionId;
}

export interface SweatshirtCartItem {
  readonly kind: 'sweatshirt';
  readonly productId: string;
  readonly patternId: string;
  readonly configuration: SweatshirtConfiguration;
  readonly quantity: number;
}

export interface PhysicalCartItem {
  readonly kind: 'physical';
  readonly productId: string;
  readonly options: Readonly<Record<string, string>>;
  readonly quantity: number;
}

export type CartItem = DigitalCartItem | SweatshirtCartItem | PhysicalCartItem;
