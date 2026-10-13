import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerOrderApiService } from './customer-order-api.service';
import type { CustomerOrderDetails as OrderDetails } from './customer-order.model';
import { CustomerOrderDetails } from './customer-order-details';

describe('CustomerOrderDetails', () => {
  let fixture: ComponentFixture<CustomerOrderDetails>;
  let element: HTMLElement;
  let result: Subject<OrderDetails>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const api = {
    getOrder: vi.fn(),
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
});
