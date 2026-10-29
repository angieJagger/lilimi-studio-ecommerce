import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerOrderApiService } from './customer-order-api.service';
import { PaymentApiService, type CheckoutSessionResult } from '../checkout/payment-api.service';
import { PaymentRedirectService } from '../checkout/payment-redirect.service';
import type { CustomerOrderDetails as OrderDetails } from './customer-order.model';
import { CustomerOrderDetails } from './customer-order-details';
import { DigitalOrderDownloadApiService } from '../digital-downloads/digital-order-download-api.service';

describe('CustomerOrderDetails', () => {
  let fixture: ComponentFixture<CustomerOrderDetails>;
  let element: HTMLElement;
  let result: Subject<OrderDetails>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let paymentResult: Subject<CheckoutSessionResult>;

  const payments = {
    resumeCheckout: vi.fn(),
  };

  const paymentRedirect = {
    redirect: vi.fn(),
  };

  const api = {
    getOrder: vi.fn(),
  };

  const downloadsApi = {
    getDownloads: vi.fn(),
  };

  const order: OrderDetails = {
    id: 'e6c91c08-bf6b-4b4a-93a4-97b90a62b055',
    createdAt: '2026-10-08T08:00:00Z',
    status: 'new',
    language: 'pl',
    currency: 'PLN',
    contact: {
      fullName: 'Anna Kowalska',
      email: 'anna@example.com',
      phone: null,
    },
    delivery: {
      kind: 'digital',
      methodId: null,
      addressLine1: null,
      addressLine2: null,
      postalCode: null,
      city: null,
      countryCode: null,
    },
    items: [
      {
        kind: 'digital',
        productId: 'pattern-001',
        productName: 'Wzór testowy',
        patternId: null,
        patternName: null,
        fit: null,
        size: null,
        color: null,
        embroideryOptionId: null,
        quantity: 1,
        unitPriceInGrosz: 2900,
        lineTotalInGrosz: 2900,
      },
    ],
    subtotalInGrosz: 2900,
    deliveryPriceInGrosz: 0,
    totalInGrosz: 2900,
  };

  beforeEach(async () => {
    result = new Subject<OrderDetails>();
    params = new BehaviorSubject(convertToParamMap({ id: order.id }));

    api.getOrder.mockReset();
    api.getOrder.mockReturnValue(result.asObservable());

    paymentResult = new Subject<CheckoutSessionResult>();

    payments.resumeCheckout.mockReset();
    payments.resumeCheckout.mockReturnValue(paymentResult.asObservable());

    paymentRedirect.redirect.mockReset();

    downloadsApi.getDownloads.mockReset();
    downloadsApi.getDownloads.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [CustomerOrderDetails, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: params.asObservable(),
          },
        },
        { provide: CustomerOrderApiService, useValue: api },
        { provide: PaymentApiService, useValue: payments },
        { provide: PaymentRedirectService, useValue: paymentRedirect },
        {
          provide: DigitalOrderDownloadApiService,
          useValue: downloadsApi,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerOrderDetails);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(details: OrderDetails = order): Promise<void> {
    result.next(details);
    result.complete();
    await fixture.whenStable();
  }

  function prepareNextRequest(): void {
    result = new Subject<OrderDetails>();
    api.getOrder.mockReturnValue(result.asObservable());
  }

  it('should load the order identified by the route', () => {
    expect(api.getOrder).toHaveBeenCalledExactlyOnceWith(order.id);
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zamówienia');
    expect(element.querySelector('.order-details__items')).toBeNull();
  });

  it('should display saved order details and formatted amounts', async () => {
    await respond();

    expect(element.textContent).toContain(order.id);
    expect(element.textContent).toContain('Nowe');
    expect(element.textContent).toContain('Anna Kowalska');
    expect(element.textContent).toContain('anna@example.com');
    expect(element.textContent).toContain('Wzór testowy');
    expect(element.textContent?.replace(/\s/g, '')).toContain('29,00zł');
    expect(element.querySelector('time')?.getAttribute('datetime')).toBe(order.createdAt);
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/account/orders');
    expect(element.querySelector('output')).toBeNull();
  });

  it('should display translated garment configuration and courier address', async () => {
    await respond({
      ...order,
      delivery: {
        kind: 'courier',
        methodId: 'dhl-courier',
        addressLine1: 'Testowa 10',
        addressLine2: 'Lokal 2',
        postalCode: '65-001',
        city: 'Zielona Góra',
        countryCode: 'PL',
      },
      items: [
        {
          ...order.items[0],
          kind: 'sweatshirt',
          productId: 'embroidered-002',
          productName: 'Bluza testowa',
          patternId: 'pattern-001',
          patternName: 'Smok',
          fit: 'men',
          size: 'M',
          color: 'black',
          embroideryOptionId: 'large-back',
          quantity: 2,
          unitPriceInGrosz: 16900,
          lineTotalInGrosz: 33800,
        },
      ],
      subtotalInGrosz: 33800,
      deliveryPriceInGrosz: 1500,
      totalInGrosz: 35300,
    });

    expect(element.textContent).toContain('Męski');
    expect(element.textContent).toContain('Czarny');
    expect(element.textContent).toContain('Duży haft z tyłu');
    expect(element.textContent).toContain('Kurier DHL');
    expect(element.querySelector('address')?.textContent).toContain('Testowa 10');
    expect(element.querySelector('address')?.textContent).toContain('Lokal 2');
    expect(element.querySelector('address')?.textContent).toContain('Zielona Góra');
    expect(element.textContent?.replace(/\s/g, '')).toContain('353,00zł');
  });

  it('should show a missing order message without retry', async () => {
    result.error(new HttpErrorResponse({ status: 404 }));
    await fixture.whenStable();

    const alert = element.querySelector('[role="alert"]')!;

    expect(alert.textContent).toContain('Nie znaleziono zamówienia');
    expect(alert.querySelector('button')).toBeNull();
    expect(element.querySelector('.order-details__items')).toBeNull();
  });

  it('should offer sign-in when the session expires', async () => {
    result.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();

    const alert = element.querySelector('[role="alert"]')!;

    expect(alert.textContent).toContain('Sesja wygasła.');
    expect(alert.querySelector('a')?.getAttribute('href')).toBe('/pl/account/login');
    expect(alert.querySelector('button')).toBeNull();
  });

  it('should retry after a temporary failure', async () => {
    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    prepareNextRequest();
    element.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await fixture.whenStable();

    expect(api.getOrder).toHaveBeenCalledTimes(2);
    expect(api.getOrder).toHaveBeenLastCalledWith(order.id);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    await respond();
    expect(element.textContent).toContain('Wzór testowy');
  });

  it('should clear previous details when the route changes', async () => {
    await respond();

    prepareNextRequest();
    params.next(convertToParamMap({ id: 'second-order' }));
    await fixture.whenStable();

    expect(api.getOrder).toHaveBeenLastCalledWith('second-order');
    expect(element.querySelector('.order-details__items')).toBeNull();
    expect(element.querySelector('output')).not.toBeNull();

    await respond({ ...order, id: 'second-order' });
    expect(element.textContent).toContain('second-order');
  });

  it('should ignore a late response from a previous route', async () => {
    const previousResult = result;

    prepareNextRequest();
    params.next(convertToParamMap({ id: 'second-order' }));
    await fixture.whenStable();

    previousResult.next(order);
    previousResult.complete();
    await fixture.whenStable();

    expect(element.querySelector('.order-details__items')).toBeNull();
    expect(element.querySelector('output')).not.toBeNull();

    await respond({ ...order, id: 'second-order' });
    expect(element.textContent).toContain('second-order');
    expect(element.textContent).not.toContain(order.id);
  });
  function paymentButton(): HTMLButtonElement | null {
    return element.querySelector('.order-details__actions button');
  }

  async function showPayableOrder(): Promise<void> {
    await respond({
      ...order,
      payment: {
        status: 'pending',
        canPay: true,
      },
    });
  }

  it('should hide payment action for a confirmed payment', async () => {
    await respond({
      ...order,
      payment: {
        status: 'succeeded',
        canPay: false,
      },
    });

    expect(element.textContent).toContain('Zapłata potwierdzona');
    expect(paymentButton()).toBeNull();
    expect(payments.resumeCheckout).not.toHaveBeenCalled();
  });

  it('should prevent duplicate clicks and redirect to the returned session', async () => {
    await showPayableOrder();

    const button = paymentButton()!;

    button.click();
    button.click();

    await fixture.whenStable();

    expect(payments.resumeCheckout).toHaveBeenCalledTimes(1);
    expect(payments.resumeCheckout).toHaveBeenCalledWith(order.id, expect.any(String));
    expect(paymentButton()?.disabled).toBe(true);

    paymentResult.next({
      paymentAttemptId: '38e96ac8-89e7-40f1-a856-086992226bdf',
      status: 'pending',
      checkoutUrl: 'https://checkout.stripe.com/c/pay/test',
    });
    paymentResult.complete();

    await fixture.whenStable();

    expect(paymentRedirect.redirect).toHaveBeenCalledExactlyOnceWith(
      'https://checkout.stripe.com/c/pay/test',
    );
  });

  it('should preserve the request key after a connection failure', async () => {
    await showPayableOrder();

    paymentButton()!.click();
    await fixture.whenStable();

    const firstKey = payments.resumeCheckout.mock.calls[0][1];

    paymentResult.error(new HttpErrorResponse({ status: 0 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się połączyć z serwerem',
    );
    expect(paymentButton()?.disabled).toBe(false);

    paymentResult = new Subject<CheckoutSessionResult>();
    payments.resumeCheckout.mockReturnValue(paymentResult.asObservable());

    paymentButton()!.click();
    await fixture.whenStable();

    expect(payments.resumeCheckout).toHaveBeenCalledTimes(2);
    expect(payments.resumeCheckout.mock.calls[1][1]).toBe(firstKey);
    expect(paymentRedirect.redirect).not.toHaveBeenCalled();
  });

  it('should use a new key after the previous session expires', async () => {
    await showPayableOrder();

    paymentButton()!.click();
    await fixture.whenStable();

    const firstKey = payments.resumeCheckout.mock.calls[0][1];

    paymentResult.next({
      paymentAttemptId: '38e96ac8-89e7-40f1-a856-086992226bdf',
      status: 'expired',
      checkoutUrl: null,
    });
    paymentResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Poprzednia próba zakończyła się lub wygasła',
    );
    expect(paymentButton()?.disabled).toBe(false);

    paymentResult = new Subject<CheckoutSessionResult>();
    payments.resumeCheckout.mockReturnValue(paymentResult.asObservable());

    paymentButton()!.click();
    await fixture.whenStable();

    expect(payments.resumeCheckout).toHaveBeenCalledTimes(2);
    expect(payments.resumeCheckout.mock.calls[1][1]).not.toBe(firstKey);
    expect(paymentRedirect.redirect).not.toHaveBeenCalled();
  });

  it('should ignore a payment response after navigating to another order', async () => {
    await showPayableOrder();

    paymentButton()!.click();
    await fixture.whenStable();

    prepareNextRequest();
    params.next(convertToParamMap({ id: 'second-order' }));
    await fixture.whenStable();

    await respond({
      ...order,
      id: 'second-order',
      payment: {
        status: 'unpaid',
        canPay: true,
      },
    });

    paymentResult.next({
      paymentAttemptId: '38e96ac8-89e7-40f1-a856-086992226bdf',
      status: 'pending',
      checkoutUrl: 'https://checkout.stripe.com/c/pay/test',
    });
    paymentResult.complete();

    await fixture.whenStable();

    expect(paymentRedirect.redirect).not.toHaveBeenCalled();
    expect(element.textContent).toContain('second-order');
  });

  it('should load downloads for a paid digital order', async () => {
    await respond({
      ...order,
      payment: {
        status: 'succeeded',
        canPay: false,
      },
    });

    expect(element.querySelector('app-digital-order-downloads')).not.toBeNull();

    expect(downloadsApi.getDownloads).toHaveBeenCalledExactlyOnceWith(order.id);
  });

  it('should not load downloads before payment is confirmed', async () => {
    await respond({
      ...order,
      payment: {
        status: 'pending',
        canPay: false,
      },
    });

    expect(element.querySelector('app-digital-order-downloads')).toBeNull();

    expect(downloadsApi.getDownloads).not.toHaveBeenCalled();
  });

  it('should not load downloads for a cancelled order', async () => {
    await respond({
      ...order,
      status: 'cancelled',
      payment: {
        status: 'succeeded',
        canPay: false,
      },
    });

    expect(element.querySelector('app-digital-order-downloads')).toBeNull();

    expect(downloadsApi.getDownloads).not.toHaveBeenCalled();
  });

  it('should not load downloads for a physical-only order', async () => {
    await respond({
      ...order,
      payment: {
        status: 'succeeded',
        canPay: false,
      },
      items: [
        {
          ...order.items[0],
          kind: 'sweatshirt',
        },
      ],
    });

    expect(element.querySelector('app-digital-order-downloads')).toBeNull();

    expect(downloadsApi.getDownloads).not.toHaveBeenCalled();
  });

  it('should load downloads for a paid mixed order with courier delivery', async () => {
    await respond({
      ...order,
      payment: {
        status: 'succeeded',
        canPay: false,
      },
      delivery: {
        kind: 'courier',
        methodId: 'dhl-courier',
        addressLine1: 'Testowa 10',
        addressLine2: null,
        postalCode: '65-001',
        city: 'Zielona Góra',
        countryCode: 'PL',
      },
      items: [
        order.items[0],
        {
          ...order.items[0],
          kind: 'sweatshirt',
          productId: 'embroidered-002',
          productName: 'Bluza testowa',
          patternId: 'pattern-001',
          patternName: 'Smok',
          fit: 'men',
          size: 'M',
          color: 'black',
          embroideryOptionId: 'large-back',
          unitPriceInGrosz: 16900,
          lineTotalInGrosz: 16900,
        },
      ],
      subtotalInGrosz: 19800,
      deliveryPriceInGrosz: 1500,
      totalInGrosz: 21300,
    });

    expect(element.querySelector('app-digital-order-downloads')).not.toBeNull();

    expect(downloadsApi.getDownloads).toHaveBeenCalledExactlyOnceWith(order.id);
  });
});
