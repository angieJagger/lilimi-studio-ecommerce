import {
  EmbroideryOptionId,
  GarmentColor,
  GarmentFit,
} from '../products/embroidered-product-options';

export const MAX_CART_ITEM_QUANTITY = 99;

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

export type CartItem = DigitalCartItem | SweatshirtCartItem;
