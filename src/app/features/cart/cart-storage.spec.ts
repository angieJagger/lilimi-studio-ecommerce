import { MAX_CART_ITEM_QUANTITY, type SweatshirtCartItem } from './cart-item.model';
import { readStoredSweatshirts } from './cart-storage';

describe('readStoredSweatshirts', () => {
  const item: SweatshirtCartItem = {
    kind: 'sweatshirt',
    productId: 'embroidered-002',
    patternId: 'pattern-001',
    configuration: {
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    },
    quantity: 1,
  };

  it('should preserve a quantity at the limit', () => {
    const result = readStoredSweatshirts([{ ...item, quantity: MAX_CART_ITEM_QUANTITY }]);

    expect(result).toEqual([{ ...item, quantity: MAX_CART_ITEM_QUANTITY }]);
  });

  it('should cap a stored quantity exceeding the limit', () => {
    const result = readStoredSweatshirts([{ ...item, quantity: MAX_CART_ITEM_QUANTITY + 1 }]);

    expect(result).toEqual([{ ...item, quantity: MAX_CART_ITEM_QUANTITY }]);
  });

  it('should cap the combined quantity of duplicate configurations', () => {
    const result = readStoredSweatshirts([
      { ...item, quantity: 60 },
      { ...item, quantity: 60 },
    ]);

    expect(result).toEqual([{ ...item, quantity: MAX_CART_ITEM_QUANTITY }]);
  });

  it.each([0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])(
    'should reject an invalid quantity: %s',
    (quantity) => {
      expect(readStoredSweatshirts([{ ...item, quantity }])).toEqual([]);
    },
  );
});
