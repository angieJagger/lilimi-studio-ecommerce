import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderPage } from './admin-order.model';
import { AdminOrders } from './admin-orders';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

describe('AdminOrders', () => {
  let fixture: ComponentFixture<unknown>;
  let router: Router;
  let element: HTMLElement;
  let result: Subject<AdminOrderPage>;

  const api = {
    getOrders: vi.fn(),
  };

  const firstPage: AdminOrderPage = {
    items: [
      {
        id: 'e6c91c08-bf6b-4b4a-93a4-97b90a62b055',
        createdAt: '2026-10-08T08:00:00Z',
        status: 'new',
        customerFullName: 'Anna Kowalska',
        customerEmail: 'anna@example.com',
        currency: 'PLN',
        totalInGrosz: 2900,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  beforeEach(async () => {
    result = new Subject<AdminOrderPage>();

    api.getOrders.mockReset();
    api.getOrders.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminOrders, getTranslocoTestingModule()],
      providers: [
        { provide: AdminOrderApiService, useValue: api },
        provideRouter([
          {
            path: 'pl/admin/orders',
            component: AdminOrders,
          },
        ]),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);

    const harness = await RouterTestingHarness.create('/pl/admin/orders');

    fixture = harness.fixture;
    element = harness.routeNativeElement!;

    await fixture.whenStable();
  });

  async function respond(page: AdminOrderPage): Promise<void> {
    result.next(page);
    result.complete();

    await fixture.whenStable();
  }

  function paginationButtons(): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('.orders-pagination button'));
  }

  it('should request the first page and show a loading message', () => {
    expect(api.getOrders).toHaveBeenCalledExactlyOnceWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zamówień');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should display order data and format the amount', async () => {
    await respond(firstPage);

    const row = element.querySelector('tbody tr')!;

    expect(row.textContent).toContain('Anna Kowalska');
    expect(row.textContent).toContain('anna@example.com');
    expect(row.textContent).toContain(firstPage.items[0].id);
    expect(row.textContent).toContain('Nowe');

    expect(row.querySelector('.order-amount')?.textContent?.replace(/\s/g, '')).toBe('29,00zł');

    expect(row.querySelector('time')?.getAttribute('datetime')).toBe(firstPage.items[0].createdAt);

    expect(element.querySelector('output')).toBeNull();
    expect(element.textContent).toContain('Strona 1 z 2');
  });

  it('should link the order reference to its details', async () => {
    await respond(firstPage);

    expect(element.querySelector<HTMLAnchorElement>('a.order-id')?.getAttribute('href')).toBe(
      `/pl/admin/orders/${firstPage.items[0].id}`,
    );
  });

  it('should show a payment cancellation warning in the order list', async () => {
    await respond({
      ...firstPage,
      items: [
        {
          ...firstPage.items[0],
          status: 'cancelled',
          paymentCancellation: {
            status: 'needs_review',
            reasonCode: 'PAYMENT_AFTER_CANCELLATION',
          },
        },
      ],
    });

    const warning = element.querySelector('.payment-cancellation-status');

    expect(warning?.textContent).toContain('Wymaga sprawdzenia');
    expect(warning?.classList.contains('payment-cancellation-status--review')).toBe(true);
    expect(element.textContent).not.toContain('PAYMENT_AFTER_CANCELLATION');
  });

  it('should show an empty state without a table or pagination', async () => {
    await respond({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    expect(element.textContent).toContain('Brak zamówień do wyświetlenia.');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('.orders-pagination')).toBeNull();
  });

  it('should show a loading error and retry the request', async () => {
    result.error(
      new HttpErrorResponse({
        status: 503,
        statusText: 'Service Unavailable',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać zamówień.',
    );

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('.orders-page > button')!.click();

    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenCalledTimes(2);
    expect(api.getOrders).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );
    expect(element.querySelector('[role="alert"]')).toBeNull();

    await respond(firstPage);

    expect(element.querySelector('table')).not.toBeNull();
  });

  it('should show an expired session message', async () => {
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
    expect(element.querySelector('table')).toBeNull();

    expect(element.querySelector<HTMLAnchorElement>('a')?.getAttribute('href')).toBe(
      '/pl/admin/login',
    );
  });

  it('should show an access denied message', async () => {
    result.error(
      new HttpErrorResponse({
        status: 403,
        statusText: 'Forbidden',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Brak uprawnień do przeglądania zamówień.',
    );
    expect(element.querySelector('table')).toBeNull();
  });

  it('should restore filters and page from the address', async () => {
    await respond(firstPage);

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    await router.navigateByUrl(
      '/pl/admin/orders?view=archive&status=cancelled' +
        '&dateFrom=2026-10-01&dateTo=2026-10-21&page=2',
    );
    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(2, 20, {
      view: 'archive',
      status: 'cancelled',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-21',
      search: undefined,
    });

    await respond({
      ...firstPage,
      page: 2,
      totalElements: 60,
      totalPages: 3,
    });

    expect(element.querySelector<HTMLSelectElement>('#order-status-filter')?.value).toBe(
      'cancelled',
    );

    expect(element.querySelector<HTMLInputElement>('#order-date-from')?.value).toBe('2026-10-01');

    expect(element.querySelector<HTMLInputElement>('#order-date-to')?.value).toBe('2026-10-21');

    expect(element.querySelector<HTMLAnchorElement>('a.order-id')?.getAttribute('href')).toBe(
      `/pl/admin/orders/${firstPage.items[0].id}` +
        '?view=archive&status=cancelled' +
        '&dateFrom=2026-10-01&dateTo=2026-10-21&page=2',
    );
  });

  it('should apply filters and return to the first page', async () => {
    await respond(firstPage);

    await router.navigateByUrl('/pl/admin/orders?view=all&page=1');
    await fixture.whenStable();

    const status = element.querySelector<HTMLSelectElement>('#order-status-filter')!;
    const from = element.querySelector<HTMLInputElement>('#order-date-from')!;
    const to = element.querySelector<HTMLInputElement>('#order-date-to')!;

    status.value = 'processing';
    from.value = '2026-10-01';
    to.value = '2026-10-21';

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element
      .querySelector<HTMLFormElement>('.order-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    const query = router.parseUrl(router.url).queryParams;

    expect(query['view']).toBe('all');
    expect(query['status']).toBe('processing');
    expect(query['dateFrom']).toBe('2026-10-01');
    expect(query['dateTo']).toBe('2026-10-21');
    expect(query['page']).toBeUndefined();

    expect(api.getOrders).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: 'processing',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-21',
      search: undefined,
    });
  });

  it('should clear filters and page while preserving the selected view', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/orders?view=archive&status=cancelled' +
        '&dateFrom=2026-10-01&dateTo=2026-10-21&page=2',
    );
    await fixture.whenStable();

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('.filter-actions button[type="button"]')!.click();

    await fixture.whenStable();

    const query = router.parseUrl(router.url).queryParams;

    expect(query).toEqual({ view: 'archive' });

    expect(api.getOrders).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({
        view: 'archive',
        status: undefined,
        dateFrom: undefined,
        dateTo: undefined,
      }),
    );

    expect(element.querySelector<HTMLSelectElement>('#order-status-filter')?.value).toBe('');

    expect(element.querySelector<HTMLInputElement>('#order-date-from')?.value).toBe('');

    expect(element.querySelector<HTMLInputElement>('#order-date-to')?.value).toBe('');
  });

  it('should change view while preserving dates and resetting status and page', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/orders?view=active&status=processing' +
        '&dateFrom=2026-10-01&dateTo=2026-10-21&page=1',
    );
    await fixture.whenStable();

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    const viewButtons = element.querySelectorAll<HTMLButtonElement>('.order-views button');

    viewButtons[1].click();

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'archive',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-21',
    });

    expect(api.getOrders).toHaveBeenLastCalledWith(0, 20, {
      view: 'archive',
      status: undefined,
      dateFrom: '2026-10-01',
      dateTo: '2026-10-21',
      search: undefined,
    });

    expect(viewButtons[0].getAttribute('aria-pressed')).toBe('false');
    expect(viewButtons[1].getAttribute('aria-pressed')).toBe('true');

    expect(element.querySelector<HTMLSelectElement>('#order-status-filter')?.value).toBe('');
  });

  it('should search all orders and clear restrictions for a new phrase', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/orders?view=archive&status=cancelled' +
        '&dateFrom=2026-10-01&dateTo=2026-10-21&page=2',
    );
    await fixture.whenStable();

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element.querySelector<HTMLInputElement>('#order-search')!.value = '  Kowalska  ';

    element
      .querySelector<HTMLFormElement>('.order-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
      search: 'Kowalska',
    });

    expect(api.getOrders).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: undefined,
      dateFrom: undefined,
      dateTo: undefined,
      search: 'Kowalska',
    });

    expect(element.querySelector<HTMLInputElement>('#order-search')?.value).toBe('Kowalska');
  });

  it('should allow narrowing results for the same search phrase', async () => {
    await respond(firstPage);

    await router.navigateByUrl('/pl/admin/orders?view=all&search=Kowalska&page=1');
    await fixture.whenStable();

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element.querySelector<HTMLSelectElement>('#order-status-filter')!.value = 'processing';
    element.querySelector<HTMLInputElement>('#order-date-from')!.value = '2026-10-01';

    element
      .querySelector<HTMLFormElement>('.order-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
      status: 'processing',
      dateFrom: '2026-10-01',
      search: 'Kowalska',
    });

    expect(api.getOrders).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: 'processing',
      dateFrom: '2026-10-01',
      dateTo: undefined,
      search: 'Kowalska',
    });
  });

  it('should clear the search together with other filters', async () => {
    await respond(firstPage);

    await router.navigateByUrl('/pl/admin/orders?view=all&search=Kowalska&page=1');
    await fixture.whenStable();

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('.filter-actions button[type="button"]')!.click();

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
    });

    expect(element.querySelector<HTMLInputElement>('#order-search')?.value).toBe('');

    expect(api.getOrders).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({
        view: 'all',
        search: undefined,
      }),
    );
  });

  it('should reject a reversed date range without fetching orders', async () => {
    await respond(firstPage);

    element.querySelector<HTMLInputElement>('#order-date-from')!.value = '2026-10-22';
    element.querySelector<HTMLInputElement>('#order-date-to')!.value = '2026-10-21';

    element
      .querySelector<HTMLFormElement>('.order-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Data początkowa nie może być późniejsza',
    );
    expect(api.getOrders).toHaveBeenCalledTimes(1);
    expect(router.url).toBe('/pl/admin/orders');
  });

  it('should ignore an older response after filters change', async () => {
    const previousResult = result;

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    await router.navigateByUrl('/pl/admin/orders?view=archive');
    await fixture.whenStable();

    previousResult.next(firstPage);
    previousResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('output')).not.toBeNull();

    await respond({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    expect(element.querySelector('output')).toBeNull();
    expect(element.querySelector('table')).toBeNull();
    expect(element.textContent).toContain('Brak zamówień do wyświetlenia.');
  });

  it('should load the next and previous pages with boundary buttons disabled', async () => {
    await respond(firstPage);

    expect(paginationButtons()[0].disabled).toBe(true);
    expect(paginationButtons()[1].disabled).toBe(false);

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    paginationButtons()[1].click();

    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(
      1,
      20,
      expect.objectContaining({ view: 'active' }),
    );
    expect(element.querySelector('.orders-pagination')).toBeNull();

    await respond({
      ...firstPage,
      page: 1,
    });

    expect(paginationButtons()[0].disabled).toBe(false);
    expect(paginationButtons()[1].disabled).toBe(true);
    expect(element.textContent).toContain('Strona 2 z 2');

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    paginationButtons()[0].click();

    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );

    await respond(firstPage);

    expect(element.textContent).toContain('Strona 1 z 2');
  });
});
