import { getCartItemKey } from './cart-item-key';
import { SweatshirtCartItem } from './cart-item.model';

describe('getCartItemKey', () => {
  const sweatshirt: SweatshirtCartItem = {
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

  it('should identify the same configuration regardless of quantity', () => {
    const threeSweatshirts: SweatshirtCartItem = {
      ...sweatshirt,
      quantity: 3,
    };

    expect(getCartItemKey(threeSweatshirts)).toBe(getCartItemKey(sweatshirt));
  });

  it('should distinguish different sizes', () => {
    const largerSweatshirt: SweatshirtCartItem = {
      ...sweatshirt,
      configuration: {
        ...sweatshirt.configuration,
        size: 'L',
      },
    };

    expect(getCartItemKey(largerSweatshirt)).not.toBe(getCartItemKey(sweatshirt));
  });

  it('should distinguish different embroidery options', () => {
    const largeEmbroidery: SweatshirtCartItem = {
      ...sweatshirt,
      configuration: {
        ...sweatshirt.configuration,
        embroideryOptionId: 'large-back',
      },
    };

    expect(getCartItemKey(largeEmbroidery)).not.toBe(getCartItemKey(sweatshirt));
  });
});
