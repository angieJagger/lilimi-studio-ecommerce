import {
  isEmbroideryOptionId,
  garmentColors,
  garmentFits,
  garmentSizes,
} from '../products/embroidered-product-options';
import { MAX_CART_ITEM_QUANTITY, PhysicalCartItem, SweatshirtCartItem } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readSweatshirt(value: unknown): SweatshirtCartItem | null {
  if (!isRecord(value) || !isRecord(value['configuration'])) {
    return null;
  }

  const productId = value['productId'];
  const patternId = value['patternId'];

  if (
    value['kind'] !== 'sweatshirt' ||
    typeof productId !== 'string' ||
    productId.trim().length === 0 ||
    productId.length > 100 ||
    typeof patternId !== 'string' ||
    patternId.trim().length === 0 ||
    patternId.length > 100
  ) {
    return null;
  }

  const configuration = value['configuration'];
  const quantity = value['quantity'];

  const fit = garmentFits.find((item) => item === configuration['fit']);

  const color = garmentColors.find((item) => item === configuration['color']);

  const embroideryOptionId = configuration['embroideryOptionId'];
  const size = configuration['size'];

  if (
    !fit ||
    !color ||
    !isEmbroideryOptionId(embroideryOptionId) ||
    typeof size !== 'string' ||
    !garmentSizes[fit].includes(size)
  ) {
    return null;
  }

  if (typeof quantity !== 'number' || !Number.isSafeInteger(quantity) || quantity < 1) {
    return null;
  }

  return {
    kind: 'sweatshirt',
    productId,
    patternId,
    configuration: {
      fit,
      size,
      color,
      embroideryOptionId,
    },
    quantity: Math.min(quantity, MAX_CART_ITEM_QUANTITY),
  };
}

export function readStoredPhysicalItems(value: unknown): PhysicalCartItem[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const items = new Map<string, PhysicalCartItem>();

  for (const entry of value) {
    if (!isRecord(entry) || entry['kind'] !== 'physical') {
      continue;
    }

    const productId = entry['productId'];
    const quantity = entry['quantity'];
    const options = entry['options'];
    if (typeof productId !== 'string' || !productId.trim() || productId.length > 100 ||
      typeof quantity !== 'number' || !Number.isSafeInteger(quantity) || quantity < 1 ||
      !isRecord(options)) {
      continue;
    }

    const pairs = Object.entries(options);
    if (pairs.length > 6 || !pairs.every(([name, option]) =>
      name.trim() && name.length <= 60 &&
      typeof option === 'string' && option.trim() && option.length <= 60,
    )) {
      continue;
    }

    const item: PhysicalCartItem = {
      kind: 'physical', productId, options: options as Record<string, string>, quantity,
    };
    const key = getCartItemKey(item);
    items.set(key, {
      ...item,
      quantity: Math.min(quantity + (items.get(key)?.quantity ?? 0), MAX_CART_ITEM_QUANTITY),
    });
  }

  return [...items.values()];
}

export function readStoredSweatshirts(value: unknown): SweatshirtCartItem[] {
  if (!Array.isArray(value)) {
    return [];
  }

  const items = new Map<string, SweatshirtCartItem>();

  for (const entry of value) {
    const item = readSweatshirt(entry);

    if (!item) {
      continue;
    }

    const key = getCartItemKey(item);
    const existing = items.get(key);
    const quantity = Math.min((existing?.quantity ?? 0) + item.quantity, MAX_CART_ITEM_QUANTITY);

    items.set(key, { ...item, quantity });
  }

  return [...items.values()];
}
