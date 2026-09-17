import { CartItem } from './cart-item.model';

export function getCartItemKey(item: CartItem): string {
  if (item.kind === 'digital') {
    return JSON.stringify([item.kind, item.productId]);
  }

  return JSON.stringify([
    item.kind,
    item.productId,
    item.patternId,
    item.configuration.fit,
    item.configuration.size,
    item.configuration.color,
    item.configuration.embroideryOptionId,
  ]);
}
