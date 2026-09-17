import {
  dragonEmbroideryOptions,
  garmentColors,
  garmentFits,
  garmentSizes,
} from '../products/embroidered-product-options';
import { SweatshirtCartItem } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readSweatshirt(value: unknown): SweatshirtCartItem | null {
  if (!isRecord(value) || !isRecord(value['configuration'])) {
    return null;
  }

  if (
    value['kind'] !== 'sweatshirt' ||
    value['productId'] !== 'embroidered-002' ||
    value['patternId'] !== 'pattern-001'
  ) {
    return null;
  }

  const configuration = value['configuration'];
  const quantity = value['quantity'];

  const fit = garmentFits.find((item) => item === configuration['fit']);

  const color = garmentColors.find((item) => item === configuration['color']);

  const embroidery = dragonEmbroideryOptions.find(
    (item) => item.id === configuration['embroideryOptionId'],
  );

  const size = configuration['size'];

  if (
    !fit ||
    !color ||
    !embroidery ||
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
    productId: 'embroidered-002',
    patternId: 'pattern-001',
    configuration: {
      fit,
      size,
      color,
      embroideryOptionId: embroidery.id,
    },
    quantity,
  };
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
    const quantity = (existing?.quantity ?? 0) + item.quantity;

    if (Number.isSafeInteger(quantity)) {
      items.set(key, { ...item, quantity });
    }
  }

  return [...items.values()];
}
