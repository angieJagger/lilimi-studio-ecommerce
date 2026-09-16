import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { EmbroideryPattern } from '../products/product.model';
import { vi } from 'vitest';

describe('CartService', () => {
  let cart: CartService;

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
});