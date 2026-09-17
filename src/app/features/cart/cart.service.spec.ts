import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { EmbroideryPattern } from '../products/product.model';
import { vi } from 'vitest';
import { SweatshirtConfiguration } from './cart-item.model';
import { getCartItemKey } from './cart-item-key';

describe('CartService', () => {
  let cart: CartService;

  const sweatshirtConfiguration: SweatshirtConfiguration = {
    fit: 'women',
    size: 'M',
    color: 'black',
    embroideryOptionId: 'small-front',
  };

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

  beforeEach(() => {
    TestBed.configureTestingModule({});
    cart = TestBed.inject(CartService);
  });

  it('should increase quantity for the same sweatshirt configuration', () => {
    // Arrange
    cart.addSweatshirt(sweatshirtConfiguration);

    // Act
    const added = cart.addSweatshirt(sweatshirtConfiguration);

    // Assert
    expect(added).toBe(true);
    expect(cart.sweatshirts()).toHaveLength(1);
    expect(cart.sweatshirts()[0].quantity).toBe(2);
    expect(cart.itemCount()).toBe(2);
    expect(cart.subtotalInGrosz()).toBe(29800);
  });

  it('should keep different sweatshirt sizes as separate items', () => {
    // Arrange
    cart.addSweatshirt(sweatshirtConfiguration);

    // Act
    cart.addSweatshirt({
      ...sweatshirtConfiguration,
      size: 'L',
    });

    // Assert
    expect(cart.sweatshirts()).toHaveLength(2);

    expect(
      cart.sweatshirts().map((item) => ({
        size: item.configuration.size,
        quantity: item.quantity,
      })),
    ).toEqual([
      { size: 'M', quantity: 1 },
      { size: 'L', quantity: 1 },
    ]);

    expect(cart.itemCount()).toBe(2);
    expect(cart.subtotalInGrosz()).toBe(29800);
  });

  it('should calculate the price of a child sweatshirt with large embroidery', () => {
    // Act
    const added = cart.addSweatshirt({
      ...sweatshirtConfiguration,
      fit: 'children',
      size: '92',
      embroideryOptionId: 'large-back',
    });

    // Assert
    expect(added).toBe(true);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(14900);
  });

  it('should reject a size that does not belong to the selected fit', () => {
    // Act
    const added = cart.addSweatshirt({
      ...sweatshirtConfiguration,
      fit: 'children',
      size: 'M',
    });

    // Assert
    expect(added).toBe(false);
    expect(cart.sweatshirts()).toEqual([]);
    expect(cart.itemCount()).toBe(0);
    expect(cart.subtotalInGrosz()).toBe(0);
  });

  it('should calculate the subtotal for digital and physical products together', () => {
    cart.addPattern(pattern);
    cart.addSweatshirt(sweatshirtConfiguration);

    expect(cart.itemCount()).toBe(2);
    expect(cart.subtotalInGrosz()).toBe(17800);
  });

  it('should remove only the selected sweatshirt configuration', () => {
    // Arrange
    cart.addSweatshirt(sweatshirtConfiguration);
    cart.addSweatshirt({
      ...sweatshirtConfiguration,
      size: 'L',
    });

    const mediumItem = cart.sweatshirts().find((item) => item.configuration.size === 'M')!;

    // Act
    cart.removeSweatshirt(getCartItemKey(mediumItem));

    // Assert
    expect(cart.sweatshirts()).toHaveLength(1);
    expect(cart.sweatshirts()[0].configuration.size).toBe('L');
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(14900);
  });

  it('should clear both digital and physical products', () => {
    cart.addPattern(pattern);
    cart.addSweatshirt(sweatshirtConfiguration);

    cart.clear();

    expect(cart.patterns()).toEqual([]);
    expect(cart.sweatshirts()).toEqual([]);
    expect(cart.itemCount()).toBe(0);
    expect(cart.subtotalInGrosz()).toBe(0);
  });

  it('should start with an empty cart', () => {
    expect(cart.patterns()).toEqual([]);
    expect(cart.itemCount()).toBe(0);
    expect(cart.subtotalInGrosz()).toBe(0);
  });

  it('should add a pattern and update the subtotal', () => {

    // Act
    cart.addPattern(pattern);

    // Assert
    expect(cart.patterns()).toEqual([pattern]);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(2900);
  });

  it('should not add the same pattern twice', () => {
    // Arrange
    cart.addPattern(pattern);

    // Act
    cart.addPattern(pattern);

    // Assert
    expect(cart.patterns()).toEqual([pattern]);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(2900);
  });

  it('should remove a pattern and recalculate the subtotal', () => {
    // Arrange
    const secondPattern: EmbroideryPattern = {
      ...pattern,
      id: 'second-test-pattern',
      slug: 'second-test-pattern',
      priceInGrosz: 1900,
    };

    cart.addPattern(pattern);
    cart.addPattern(secondPattern);

    // Act
    cart.removeProduct(pattern.id);

    // Assert
    expect(cart.patterns()).toEqual([secondPattern]);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(1900);
  });

  it('should clear all products and reset the subtotal', () => {
    // Arrange
    const secondPattern: EmbroideryPattern = {
      ...pattern,
      id: 'second-test-pattern',
      slug: 'second-test-pattern',
      priceInGrosz: 1900,
    };

    cart.addPattern(pattern);
    cart.addPattern(secondPattern);

    // Act
    cart.clear();

    // Assert
    expect(cart.patterns()).toEqual([]);
    expect(cart.itemCount()).toBe(0);
    expect(cart.subtotalInGrosz()).toBe(0);
  });

});

describe('CartService persistence', () => {
  const storageKey = 'lilimi.cart.v1';
  const sweatshirtStorageKey = 'lilimi.cart.sweatshirts.v1';
  let storage: Map<string, string>;

  beforeEach(() => {
    storage = new Map<string, string>();

    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value);
      },
    });

    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should restore valid patterns without duplicates', () => {
    // Arrange
    storage.set(
      storageKey,
      JSON.stringify(['pattern-001', 'pattern-001', 'unknown-product', 'embroidered-001', 123]),
    );

    // Act
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Assert
    expect(cart.patterns().map((product) => product.id)).toEqual(['pattern-001']);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(2900);
  });

  it('should recover from malformed stored data', () => {
    // Arrange
    storage.set(storageKey, '{broken json');

    // Act
    const cart = TestBed.inject(CartService);

    // Assert
    expect(() => TestBed.tick()).not.toThrow();
    expect(cart.patterns()).toEqual([]);
    expect(cart.subtotalInGrosz()).toBe(0);
  });

  it('should save the remaining product IDs after removal', () => {
    // Arrange
    storage.set(storageKey, JSON.stringify(['pattern-001', 'pattern-002']));

    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Act
    cart.removeProduct('pattern-001');

    // Assert
    expect(JSON.parse(storage.get(storageKey)!)).toEqual(['pattern-002']);
  });

  it('should save only product IDs after adding a pattern', () => {
    // Arrange
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    const pattern: EmbroideryPattern = {
      id: 'pattern-001',
      slug: 'forest-dragon',
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

    // Act
    cart.addPattern(pattern);

    // Assert
    expect(JSON.parse(storage.get(storageKey)!)).toEqual(['pattern-001']);
  });

  it('should persist an empty cart after clearing it', () => {
    // Arrange
    storage.set(storageKey, JSON.stringify(['pattern-001', 'pattern-002']));

    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Act
    cart.clear();

    // Assert
    expect(JSON.parse(storage.get(storageKey)!)).toEqual([]);
  });

  it('should restore a sweatshirt configuration and quantity', () => {
    // Arrange
    storage.set(
      sweatshirtStorageKey,
      JSON.stringify([
        {
          kind: 'sweatshirt',
          productId: 'embroidered-002',
          patternId: 'pattern-001',
          configuration: {
            fit: 'children',
            size: '92',
            color: 'navy',
            embroideryOptionId: 'large-back',
          },
          quantity: 2,
        },
      ]),
    );

    // Act
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Assert
    expect(cart.sweatshirts()).toEqual([
      {
        kind: 'sweatshirt',
        productId: 'embroidered-002',
        patternId: 'pattern-001',
        configuration: {
          fit: 'children',
          size: '92',
          color: 'navy',
          embroideryOptionId: 'large-back',
        },
        quantity: 2,
      },
    ]);

    expect(cart.itemCount()).toBe(2);
    expect(cart.subtotalInGrosz()).toBe(29800);
  });

  it('should save a sweatshirt configuration after adding it', () => {
    // Arrange
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Act
    cart.addSweatshirt({
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    });

    // Assert
    expect(JSON.parse(storage.get(sweatshirtStorageKey)!)).toEqual([
      {
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
      },
    ]);
  });

  it('should reject stored sweatshirts with an invalid size or quantity', () => {
    // Arrange
    const validItem = {
      kind: 'sweatshirt',
      productId: 'embroidered-002',
      patternId: 'pattern-001',
      configuration: {
        fit: 'children',
        size: '92',
        color: 'navy',
        embroideryOptionId: 'large-back',
      },
      quantity: 1,
    };

    storage.set(
      sweatshirtStorageKey,
      JSON.stringify([
        {
          ...validItem,
          configuration: {
            ...validItem.configuration,
            size: 'M',
          },
        },
        { ...validItem, quantity: 0 },
        { ...validItem, quantity: -1 },
        { ...validItem, quantity: 1.5 },
        validItem,
      ]),
    );

    // Act
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    // Assert
    expect(cart.sweatshirts()).toEqual([validItem]);
    expect(cart.itemCount()).toBe(1);
    expect(cart.subtotalInGrosz()).toBe(14900);
  });

  it('should persist an empty sweatshirt list after clearing the cart', () => {
    // Arrange
    const cart = TestBed.inject(CartService);
    TestBed.tick();

    cart.addSweatshirt({
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    });

    // Act
    cart.clear();

    // Assert
    expect(cart.sweatshirts()).toEqual([]);
    expect(JSON.parse(storage.get(sweatshirtStorageKey)!)).toEqual([]);
  });
});
