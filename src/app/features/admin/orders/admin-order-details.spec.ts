import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderDetails as OrderDetails } from './admin-order.model';
import { AdminOrderDetails } from './admin-order-details';

describe('AdminOrderDetails', () => {
  let fixture: ComponentFixture<AdminOrderDetails>;
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
      imports: [AdminOrderDetails, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: params.asObservable() },
        },
        { provide: AdminOrderApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminOrderDetails);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(details: OrderDetails): Promise<void> {
    result.next(details);
    result.complete();

    await fixture.whenStable();
  }

  it('should request the order from the route and show loading', () => {
    expect(api.getOrder).toHaveBeenCalledExactlyOnceWith(order.id);
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zamówienia');
    expect(element.querySelector('.order-card')).toBeNull();
  });

  it('should display contact, digital products and the total', async () => {
    await respond(order);

    expect(element.textContent).toContain(order.id);
    expect(element.textContent).toContain('Anna Kowalska');
    expect(element.textContent).toContain('anna@example.com');
    expect(element.textContent).toContain('Wzór testowy');
    expect(element.textContent).toContain('Produkty cyfrowe — bez wysyłki.');

    expect(element.querySelector('.order-total dd')?.textContent?.replace(/\s/g, '')).toBe(
      '29,00zł',
    );

    expect(element.querySelector('output')).toBeNull();
    expect(element.querySelector('.back-link')?.getAttribute('href')).toBe('/pl/admin/orders');
  });

  it('should show a missing order message without a retry button', async () => {
    result.error(
      new HttpErrorResponse({
        status: 404,
        statusText: 'Not Found',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie znaleziono zamówienia.',
    );
    expect(element.querySelector('button')).toBeNull();
    expect(element.querySelector('.order-card')).toBeNull();
  });

  it('should retry after a connection error', async () => {
    result.error(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać szczegółów zamówienia.',
    );

    result = new Subject<OrderDetails>();
    api.getOrder.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getOrder).toHaveBeenCalledTimes(2);
    expect(api.getOrder).toHaveBeenLastCalledWith(order.id);

    await respond(order);

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.textContent).toContain('Wzór testowy');
  });

  it('should offer login when the session expires', async () => {
    result.error(
      new HttpErrorResponse({
        status: 401,
        statusText: 'Unauthorized',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Sesja wygasła. Zaloguj się ponownie.',
    );

    expect(element.querySelector<HTMLAnchorElement>('a.button')?.getAttribute('href')).toBe(
      '/pl/admin/login',
    );

    expect(element.querySelector('.order-card')).toBeNull();
  });

  it('should cancel the previous request when the order id changes', async () => {
    const previousResult = result;
    const nextId = 'cab06982-7822-4b40-9f7a-87c969209dce';

    result = new Subject<OrderDetails>();
    api.getOrder.mockReturnValue(result.asObservable());

    params.next(convertToParamMap({ id: nextId }));

    await fixture.whenStable();

    expect(api.getOrder).toHaveBeenLastCalledWith(nextId);

    previousResult.next(order);
    previousResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('.order-card')).toBeNull();
    expect(element.querySelector('output')).not.toBeNull();

    await respond({
      ...order,
      id: nextId,
      contact: {
        ...order.contact,
        fullName: 'Jan Nowak',
      },
    });

    expect(element.textContent).toContain(nextId);
    expect(element.textContent).toContain('Jan Nowak');
    expect(element.textContent).not.toContain('Anna Kowalska');
  });
  it('should display sweatshirt options, courier address and delivery cost', async () => {
    await respond({
      ...order,
      contact: {
        ...order.contact,
        phone: '123456789',
      },
      delivery: {
        kind: 'courier',
        methodId: 'dhl-courier',
        addressLine1: 'Testowa 10',
        addressLine2: 'Mieszkanie 2',
        postalCode: '65-001',
        city: 'Zielona Góra',
        countryCode: 'PL',
      },
      items: [
        {
          kind: 'sweatshirt',
          productId: 'embroidered-002',
          productName: 'Bluza haftowana',
          patternId: 'pattern-001',
          patternName: 'Leśny smok',
          fit: 'men',
          size: 'M',
          color: 'black',
          embroideryOptionId: 'small-front',
          quantity: 2,
          unitPriceInGrosz: 14900,
          lineTotalInGrosz: 29800,
        },
      ],
      subtotalInGrosz: 29800,
      deliveryPriceInGrosz: 1200,
      totalInGrosz: 31000,
    });

    expect(element.textContent).toContain('123456789');
    expect(element.textContent).toContain('Kurier DHL');

    const address = element.querySelector('address')!;

    expect(address.textContent).toContain('Testowa 10');
    expect(address.textContent).toContain('Mieszkanie 2');
    expect(address.textContent).toContain('65-001');
    expect(address.textContent).toContain('Zielona Góra');

    const item = element.querySelector('.order-item')!;

    expect(item.textContent).toContain('Bluza haftowana');
    expect(item.textContent).toContain('Leśny smok');
    expect(item.textContent).toContain('Męski');
    expect(item.textContent).toContain('Czarny');
    expect(item.textContent).toContain('Mały haft z przodu');

    const values = Array.from(item.querySelectorAll('dd')).map((cell) =>
      cell.textContent?.trim().replace(/\s/g, ''),
    );

    expect(values).toEqual([
      'Męski',
      'M',
      'Czarny',
      'Małyhaftzprzodu',
      '2',
      '149,00zł',
      '298,00zł',
    ]);

    expect(element.querySelector('.order-total dd')?.textContent?.replace(/\s/g, '')).toBe(
      '310,00zł',
    );

    expect(element.textContent).not.toContain('Produkty cyfrowe — bez wysyłki.');
  });
});
