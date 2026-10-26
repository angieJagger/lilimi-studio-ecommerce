import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import type { CurrentUser } from '../../core/auth/auth.model';
import { CustomerProfileApiService } from '../../core/customer/customer-profile-api.service';
import type { CustomerProfile } from '../../core/customer/customer-profile.model';
import { TranslocoService } from '@jsverse/transloco';
import { vi } from 'vitest';

import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';
import { CartService } from '../cart/cart.service';
import { EmbroideryPattern } from '../products/product.model';
import { Checkout } from './checkout';
import { OrderApiService } from './order-api.service';
import { OrderSubmissionKeyService } from './order-submission-key.service';
import type { CreateOrderResponse } from './order.model';

describe('Checkout', () => {
  let fixture: ComponentFixture<Checkout>;
  let cart: CartService;
  let element: HTMLElement;
  let orderResult: Subject<CreateOrderResponse>;

  let sessionResult: Subject<CurrentUser | null>;
  let profileResult: Subject<CustomerProfile>;
  let currentUser: CurrentUser | null;

  const sessionMock = {
    refresh: vi.fn(),
    user: vi.fn(),
  };
  const profileApiMock = {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
  };
  const savedProfile: CustomerProfile = {
    email: 'saved@example.com',
    firstName: 'Ewa',
    lastName: 'Nowak',
    phone: '500600700',
    addressLine1: 'Zapisana 12',
    addressLine2: 'mieszkanie 3',
    postalCode: '00-001',
    city: 'Warszawa',
    countryCode: 'PL',
    version: 2,
  };

  const orderApiMock = {
    createOrder: vi.fn(),
  };

  const submissionKeysMock = {
    getKey: vi.fn(),
    markCompleted: vi.fn(),
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

  beforeEach(async () => {
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
    });

    currentUser = null;
    sessionResult = new Subject<CurrentUser | null>();
    profileResult = new Subject<CustomerProfile>();
    sessionMock.refresh.mockReset().mockReturnValue(sessionResult.asObservable());
    sessionMock.user.mockReset().mockImplementation(() => currentUser);
    profileApiMock.getProfile.mockReset().mockReturnValue(profileResult.asObservable());
    profileApiMock.updateProfile.mockReset();

    orderResult = new Subject<CreateOrderResponse>();
    orderApiMock.createOrder.mockReset();
    orderApiMock.createOrder.mockReturnValue(orderResult.asObservable());
    submissionKeysMock.getKey.mockReset();
    submissionKeysMock.getKey.mockResolvedValue('7c82dd0f-9289-4a9e-bf58-05b415d6da48');
    submissionKeysMock.markCompleted.mockReset();

    await TestBed.configureTestingModule({
      imports: [Checkout, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        provideSweatshirtVariantsTesting(),
        provideProductCatalogTesting(),
        { provide: OrderApiService, useValue: orderApiMock },
        { provide: AuthSessionService, useValue: sessionMock },
        { provide: CustomerProfileApiService, useValue: profileApiMock },
        {
          provide: OrderSubmissionKeyService,
          useValue: submissionKeysMock,
        },
      ],
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

  async function reviewDigitalOrder(): Promise<void> {
    cart.addPattern(pattern);
    await fixture.whenStable();

    await fillField('fullName', 'Anna Kowalska');
    await fillField('email', 'anna@example.com');
    await continueToReview();
  }

  async function submitReviewedOrder(): Promise<void> {
    element.querySelector<HTMLButtonElement>('button[aria-busy]')!.click();

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

  it('should prepare a digital order without an address or prices', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();

    await fillField('fullName', '  Anna Kowalska  ');
    await fillField('email', 'anna@example.com');
    await continueToReview();

    const request = fixture.componentInstance['orderRequest']();

    expect(request).toEqual({
      language: 'pl',
      contact: {
        fullName: 'Anna Kowalska',
        email: 'anna@example.com',
      },
      delivery: {
        kind: 'digital',
      },
      items: [
        {
          kind: 'digital',
          productId: pattern.id,
          quantity: 1,
        },
      ],
    });
  });

  it('should prepare a mixed order with the selected variant and quantity', async () => {
    cart.addPattern(pattern);
    await addSweatshirt();
    await addSweatshirt();

    await fillField('fullName', 'Anna Kowalska');
    await fillField('email', 'anna@example.com');
    await fillField('phone', ' 500600700 ');

    await selectCourier();

    await fillField('addressLine1', '  ul. Kwiatowa 10  ');
    await fillField('addressLine2', '  mieszkanie 5  ');
    await fillField('postalCode', '00-001');
    await fillField('city', '  Warszawa  ');
    await continueToReview();

    const request = fixture.componentInstance['orderRequest']();

    expect(request).toEqual({
      language: 'pl',
      contact: {
        fullName: 'Anna Kowalska',
        email: 'anna@example.com',
        phone: '500600700',
      },
      delivery: {
        kind: 'courier',
        methodId: 'dhl-courier',
        address: {
          addressLine1: 'ul. Kwiatowa 10',
          addressLine2: 'mieszkanie 5',
          postalCode: '00-001',
          city: 'Warszawa',
          countryCode: 'PL',
        },
      },
      items: [
        {
          kind: 'digital',
          productId: pattern.id,
          quantity: 1,
        },
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
          quantity: 2,
        },
      ],
    });
  });

  it('should prevent duplicate submission while sending', async () => {
    await reviewDigitalOrder();

    const button = element.querySelector<HTMLButtonElement>('button[aria-busy]')!;

    button.click();
    button.click();

    await fixture.whenStable();

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(1);
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');

    expect(orderApiMock.createOrder).toHaveBeenCalledWith(
      {
        language: 'pl',
        contact: {
          fullName: 'Anna Kowalska',
          email: 'anna@example.com',
        },
        delivery: {
          kind: 'digital',
        },
        items: [
          {
            kind: 'digital',
            productId: pattern.id,
            quantity: 1,
          },
        ],
      },
      expect.any(String),
    );
  });

  it('should show server confirmation and clear the cart', async () => {
    await reviewDigitalOrder();

    await submitReviewedOrder();

    orderResult.next({
      id: '7c82dd0f-9289-4a9e-bf58-05b415d6da48',
      createdAt: '2026-10-05T12:00:00Z',
      status: 'new',
      currency: 'PLN',
      subtotalInGrosz: 3100,
      deliveryPriceInGrosz: 0,
      totalInGrosz: 3100,
      paymentsEnabled: true,
    });
    orderResult.complete();

    await fixture.whenStable();

    expect(cart.itemCount()).toBe(0);
    expect(element.querySelector('#checkout-success-title')).not.toBeNull();
    expect(element.textContent).toContain('7c82dd0f-9289-4a9e-bf58-05b415d6da48');
    expect(element.textContent).not.toContain('Twój koszyk jest pusty');

    expect(element.querySelector('.checkout__total--final dd')?.textContent).toContain('31,00');

    expect(element.querySelector('#checkout-form')).toBeNull();

    const paymentButton = element.querySelector<HTMLButtonElement>('.checkout__actions button');

    expect(paymentButton?.textContent).toContain('Przejdź do płatności testowej');
    expect(paymentButton?.disabled).toBe(false);
    expect(paymentButton?.getAttribute('aria-busy')).toBe('false');

    expect(submissionKeysMock.markCompleted).toHaveBeenCalledWith(
      '7c82dd0f-9289-4a9e-bf58-05b415d6da48',
    );
  });

  it('should preserve the cart and show an unavailable product error', async () => {
    await reviewDigitalOrder();

    await submitReviewedOrder();

    orderResult.error(
      new HttpErrorResponse({
        status: 409,
        statusText: 'Conflict',
        error: {
          code: 'ORDER_PRODUCT_UNAVAILABLE',
          productId: pattern.id,
        },
      }),
    );

    await fixture.whenStable();

    expect(cart.itemCount()).toBe(1);
    expect(element.querySelector('#checkout-success-title')).toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('niedostępny');

    expect(element.querySelector<HTMLButtonElement>('button[aria-busy]')?.disabled).toBe(false);

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(1);
  });

  it('should preserve the cart, contact details and submission key after HTTP 429', async () => {
    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();

    await reviewDigitalOrder();

    const retryResult = new Subject<CreateOrderResponse>();

    orderApiMock.createOrder
      .mockReturnValueOnce(orderResult.asObservable())
      .mockReturnValueOnce(retryResult.asObservable());

    await submitReviewedOrder();

    const [firstRequest, firstKey] = orderApiMock.createOrder.mock.calls[0];

    orderResult.error(
      new HttpErrorResponse({
        status: 429,
        error: {
          code: 'ORDER_RATE_LIMITED',
        },
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Too many orders in a short time. Please wait before trying again.',
    );

    expect(cart.itemCount()).toBe(1);
    expect(submissionKeysMock.markCompleted).not.toHaveBeenCalled();
    expect(element.querySelector('#checkout-success-title')).toBeNull();

    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')!.value).toBe(
      'Anna Kowalska',
    );

    expect(element.querySelector<HTMLInputElement>('#checkout-email')!.value).toBe(
      'anna@example.com',
    );

    expect(element.querySelector<HTMLButtonElement>('button[aria-busy]')!.disabled).toBe(false);

    await submitReviewedOrder();

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(2);
    expect(orderApiMock.createOrder.mock.calls[1][0]).toEqual(firstRequest);
    expect(orderApiMock.createOrder.mock.calls[1][1]).toBe(firstKey);

    retryResult.error(new HttpErrorResponse({ status: 429 }));
    await fixture.whenStable();
  });

  it('should reuse the same key after a connection error', async () => {
    await reviewDigitalOrder();

    const retryResult = new Subject<CreateOrderResponse>();

    orderApiMock.createOrder
      .mockReturnValueOnce(orderResult.asObservable())
      .mockReturnValueOnce(retryResult.asObservable());

    await submitReviewedOrder();

    const firstKey = orderApiMock.createOrder.mock.calls[0][1];

    orderResult.error(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
      }),
    );

    await fixture.whenStable();

    expect(cart.itemCount()).toBe(1);

    await submitReviewedOrder();

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(2);
    expect(orderApiMock.createOrder.mock.calls[1][1]).toBe(firstKey);
    expect(orderApiMock.createOrder.mock.calls[1][0]).toEqual(
      orderApiMock.createOrder.mock.calls[0][0],
    );

    retryResult.next({
      id: '7c82dd0f-9289-4a9e-bf58-05b415d6da48',
      createdAt: '2026-10-06T12:00:00Z',
      status: 'new',
      currency: 'PLN',
      subtotalInGrosz: 2900,
      deliveryPriceInGrosz: 0,
      totalInGrosz: 2900,
    });
    retryResult.complete();

    await fixture.whenStable();

    expect(cart.itemCount()).toBe(0);
    expect(element.querySelector('#checkout-success-title')).not.toBeNull();
  });

  it('should create a new key when rejected order details change', async () => {
    await reviewDigitalOrder();

    submissionKeysMock.getKey
      .mockResolvedValueOnce('7c82dd0f-9289-4a9e-bf58-05b415d6da48')
      .mockResolvedValueOnce('43c5fc12-5ed2-49d0-bbaa-dc237a908c93');

    const retryResult = new Subject<CreateOrderResponse>();

    orderApiMock.createOrder
      .mockReturnValueOnce(orderResult.asObservable())
      .mockReturnValueOnce(retryResult.asObservable());

    await submitReviewedOrder();

    const firstKey = orderApiMock.createOrder.mock.calls[0][1];

    orderResult.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'ORDER_VALIDATION_FAILED',
        },
      }),
    );

    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('.checkout__review button')!.click();

    await fixture.whenStable();

    await fillField('email', 'new@example.com');
    await continueToReview();

    await submitReviewedOrder();

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(2);
    expect(orderApiMock.createOrder.mock.calls[1][1]).not.toBe(firstKey);
    expect(orderApiMock.createOrder.mock.calls[1][0].contact.email).toBe('new@example.com');

    retryResult.complete();
    await fixture.whenStable();
  });

  async function customerSession(): Promise<void> {
    currentUser = { email: savedProfile.email, roles: ['CUSTOMER'] };
    sessionResult.next(currentUser);
    sessionResult.complete();
    await fixture.whenStable();
  }

  async function deliverProfile(): Promise<void> {
    profileResult.next(savedProfile);
    profileResult.complete();
    await fixture.whenStable();
  }

  it('should keep guest checkout without requesting a profile', async () => {
    sessionResult.next(null);
    sessionResult.complete();
    await reviewDigitalOrder();
    expect(profileApiMock.getProfile).not.toHaveBeenCalled();
    expect(fixture.componentInstance['orderRequest']()?.contact.email).toBe('anna@example.com');
  });

  it('should not request a customer profile for an admin', async () => {
    currentUser = { email: 'admin@example.com', roles: ['ADMIN'] };
    sessionResult.next(currentUser);
    sessionResult.complete();
    await reviewDigitalOrder();
    expect(profileApiMock.getProfile).not.toHaveBeenCalled();
  });

  it('should fill saved contact and address while leaving delivery selection to the customer', async () => {
    await addSweatshirt();
    await customerSession();
    await deliverProfile();
    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')?.value).toBe('Ewa Nowak');
    expect(element.querySelector<HTMLInputElement>('#checkout-email')?.value).toBe(
      savedProfile.email,
    );
    expect(
      element.querySelector<HTMLInputElement>('input[name="deliveryMethod"]:checked'),
    ).toBeNull();

    await selectCourier();
    expect(element.querySelector<HTMLInputElement>('#checkout-addressLine1')?.value).toBe(
      'Zapisana 12',
    );
    expect(element.querySelector<HTMLInputElement>('#checkout-city')?.value).toBe('Warszawa');
    await continueToReview();
    expect(fixture.componentInstance['orderRequest']()?.delivery).toMatchObject({
      kind: 'courier',
      address: { addressLine1: 'Zapisana 12', city: 'Warszawa', countryCode: 'PL' },
    });
  });

  it('should preserve typed fields when the profile response arrives late', async () => {
    await addSweatshirt();
    await selectCourier();
    await customerSession();
    await fillField('fullName', 'Moje dane');
    await fillField('city', 'Kraków');
    await deliverProfile();

    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')?.value).toBe('Moje dane');
    expect(element.querySelector<HTMLInputElement>('#checkout-city')?.value).toBe('Kraków');
    expect(element.querySelector<HTMLInputElement>('#checkout-email')?.value).toBe(
      savedProfile.email,
    );
    expect(element.querySelector<HTMLInputElement>('#checkout-addressLine1')?.value).toBe(
      'Zapisana 12',
    );
  });

  it('should not refill a field that the customer deliberately cleared', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();
    await customerSession();
    await fillField('fullName', 'Temporary name');
    await fillField('fullName', '');
    await deliverProfile();

    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')?.value).toBe('');
    expect(element.querySelector<HTMLInputElement>('#checkout-email')?.value).toBe(
      savedProfile.email,
    );
  });

  it('should not change order details after the customer enters review', async () => {
    await customerSession();
    await reviewDigitalOrder();
    await deliverProfile();

    expect(fixture.componentInstance['orderRequest']()?.contact).toEqual({
      fullName: 'Anna Kowalska',
      email: 'anna@example.com',
    });
    expect(element.querySelector('.checkout__review')?.textContent).not.toContain('Ewa Nowak');
  });

  it('should allow manual checkout if the profile cannot be loaded', async () => {
    await customerSession();
    profileResult.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();
    await reviewDigitalOrder();

    expect(element.querySelector('.checkout__review')).not.toBeNull();
    expect(fixture.componentInstance['orderRequest']()?.contact.email).toBe('anna@example.com');
    expect(fixture.componentInstance['profileLoadFailed']()).toBe(true);
  });

  it('should discard a profile response if the signed-in account has changed', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();
    await customerSession();
    currentUser = { email: 'other@example.com', roles: ['CUSTOMER'] };
    await deliverProfile();

    expect(element.querySelector<HTMLInputElement>('#checkout-email')?.value).toBe('');
    expect(element.querySelector<HTMLInputElement>('#checkout-fullName')?.value).toBe('');
  });

  it('should use edited details for a digital order without saving them to the profile', async () => {
    cart.addPattern(pattern);
    await fixture.whenStable();
    await customerSession();
    await deliverProfile();
    await fillField('fullName', 'Odbiorca zamówienia');
    await continueToReview();
    await submitReviewedOrder();

    expect(orderApiMock.createOrder).toHaveBeenCalledTimes(1);
    const request = orderApiMock.createOrder.mock.calls[0][0];
    expect(request.contact.fullName).toBe('Odbiorca zamówienia');
    expect(request.contact.email).toBe(savedProfile.email);
    expect(request.delivery).toEqual({ kind: 'digital' });
    expect(profileApiMock.updateProfile).not.toHaveBeenCalled();
    expect(savedProfile.firstName).toBe('Ewa');
  });
  it('should hide payment actions when payments are disabled', async () => {
    await reviewDigitalOrder();
    await submitReviewedOrder();

    orderResult.next({
      id: '7c82dd0f-9289-4a9e-bf58-05b415d6da48',
      createdAt: '2026-10-05T12:00:00Z',
      status: 'new',
      currency: 'PLN',
      subtotalInGrosz: 3100,
      deliveryPriceInGrosz: 0,
      totalInGrosz: 3100,
      paymentsEnabled: false,
    });
    orderResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('#checkout-success-title')).not.toBeNull();
    expect(element.querySelector('.checkout__actions button')).toBeNull();
    expect(element.textContent).not.toContain('Płatność testowa Stripe');
  });
});
