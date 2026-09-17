import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { Checkout } from './checkout';
import { CartService } from '../cart/cart.service';
import { EmbroideryPattern } from '../products/product.model';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';

describe('Checkout', () => {
  let fixture: ComponentFixture<Checkout>;
  let cart: CartService;
  let element: HTMLElement;

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

  beforeEach(async () => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
    });

    await TestBed.configureTestingModule({
      imports: [Checkout, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    cart = TestBed.inject(CartService);
    fixture = TestBed.createComponent(Checkout);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function addSweatshirt(): Promise<void> {
    cart.addSweatshirt({
      fit: 'women',
      size: 'M',
      color: 'black',
      embroideryOptionId: 'small-front',
    });

    await fixture.whenStable();
  }

  async function selectCourier(): Promise<void> {
    element
      .querySelector<HTMLInputElement>('input[name="deliveryMethod"][value="dhl-courier"]')!
      .click();

    await fixture.whenStable();
  }

  async function fillField(name: string, value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(`#checkout-${name}`)!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function continueToReview(): Promise<void> {
    element
      .querySelector<HTMLButtonElement>('button[type="submit"][form="checkout-form"]')!
      .click();

    await fixture.whenStable();
  }

  it('should show a return to products for an empty cart', () => {
    expect(element.textContent).toContain('Twój koszyk jest pusty');
    expect(element.querySelector('form')).toBeNull();

    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/products');
  });

  it('should not require delivery for digital products', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();

    expect(element.querySelector('.checkout__delivery')).toBeNull();
    expect(element.querySelector('.checkout__address')).toBeNull();

    expect(element.querySelector('.checkout__total--final dd')?.textContent).toContain('29,00');
  });

  it('should add one delivery charge to a mixed cart', async () => {
    cart.addPattern(pattern);
    await addSweatshirt();

    expect(element.querySelector('.checkout__total--final dd')?.textContent).toContain(
      'Oczekuje na koszt dostawy',
    );

    await selectCourier();

    expect(element.querySelector('.checkout__address')).not.toBeNull();

    expect(element.querySelector('.checkout__total--final dd')?.textContent).toContain('190,00');
  });

  it('should show an error for an incorrectly formatted postal code', async () => {
    await addSweatshirt();
    await selectCourier();

    const postalCode = element.querySelector<HTMLInputElement>('#checkout-postalCode')!;

    postalCode.value = '12345';
    postalCode.dispatchEvent(new Event('input', { bubbles: true }));
    postalCode.dispatchEvent(new Event('blur'));

    await fixture.whenStable();

    expect(postalCode.getAttribute('aria-invalid')).toBe('true');
    expect(element.querySelector('#checkout-postalCode-errors')?.textContent).toContain('00-001');

    postalCode.value = '12-345';
    postalCode.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();

    expect(postalCode.getAttribute('aria-invalid')).toBeNull();
    expect(element.querySelector('#checkout-postalCode-errors')).toBeNull();
  });

  it('should show required contact errors when continuing with empty fields', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();

    await continueToReview();

    expect(element.querySelector('#checkout-fullName')?.getAttribute('aria-invalid')).toBe('true');

    expect(element.querySelector('#checkout-email')?.getAttribute('aria-invalid')).toBe('true');

    expect(element.querySelector('.checkout__review')).toBeNull();
  });

  it('should reject a name containing only spaces', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();

    await fillField('fullName', '   ');
    await fillField('email', 'anna@example.com');
    await continueToReview();

    expect(element.querySelector('#checkout-fullName')?.getAttribute('aria-invalid')).toBe('true');

    expect(element.querySelector('.checkout__review')).toBeNull();
  });

  it('should require a courier and address for a physical product', async () => {
    await addSweatshirt();

    await fillField('fullName', 'Anna Kowalska');
    await fillField('email', 'anna@example.com');
    await continueToReview();

    expect(element.querySelector('#checkout-delivery-error')).not.toBeNull();
    expect(element.querySelector('.checkout__review')).toBeNull();

    await selectCourier();
    await continueToReview();

    expect(element.querySelector('#checkout-delivery-error')).toBeNull();

    for (const name of ['addressLine1', 'postalCode', 'city']) {
      expect(element.querySelector(`#checkout-${name}`)?.getAttribute('aria-invalid')).toBe('true');
    }

    expect(element.querySelector('.checkout__review')).toBeNull();

    await fillField('addressLine1', 'ul. Kwiatowa 10');
    await fillField('postalCode', '00-001');
    await fillField('city', 'Warszawa');
    await continueToReview();

    const review = element.querySelector('.checkout__review');

    expect(review).not.toBeNull();
    expect(review?.textContent).toContain('ul. Kwiatowa 10');
    expect(review?.textContent).toContain('Warszawa');
  });

  it('should review a digital order and preserve contact details when editing', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();

    await fillField('fullName', 'Anna Kowalska');
    await fillField('email', 'anna@example.com');
    await continueToReview();

    const review = element.querySelector('.checkout__review');

    expect(review).not.toBeNull();
    expect(review?.textContent).toContain('Anna Kowalska');
    expect(review?.textContent).toContain('anna@example.com');

    expect(element.querySelector<HTMLFormElement>('#checkout-form')?.hidden).toBe(true);

    review!.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    expect(element.querySelector('.checkout__review')).toBeNull();

    expect(element.querySelector<HTMLFormElement>('#checkout-form')?.hidden).toBe(false);

    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')?.value).toBe(
      'Anna Kowalska',
    );

    expect(element.querySelector<HTMLInputElement>('#checkout-email')?.value).toBe(
      'anna@example.com',
    );
  });
});
