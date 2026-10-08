import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderPage } from './admin-order.model';
import { AdminOrders } from './admin-orders';
import { provideRouter } from '@angular/router';

describe('AdminOrders', () => {
  let fixture: ComponentFixture<AdminOrders>;
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
      providers: [{ provide: AdminOrderApiService, useValue: api }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminOrders);
    element = fixture.nativeElement;

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
    expect(api.getOrders).toHaveBeenCalledExactlyOnceWith(0);
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

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenCalledTimes(2);
    expect(api.getOrders).toHaveBeenLastCalledWith(0);
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

    expect(
      element.querySelector<HTMLAnchorElement>('a')?.getAttribute('href'),
    ).toBe('/pl/admin/login');
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

  it('should load the next and previous pages with boundary buttons disabled', async () => {
    await respond(firstPage);

    expect(paginationButtons()[0].disabled).toBe(true);
    expect(paginationButtons()[1].disabled).toBe(false);

    result = new Subject<AdminOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());

    paginationButtons()[1].click();

    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(1);
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

    expect(api.getOrders).toHaveBeenLastCalledWith(0);

    await respond(firstPage);

    expect(element.textContent).toContain('Strona 1 z 2');
  });
});
