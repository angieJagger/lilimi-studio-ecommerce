import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerOrderApiService } from './customer-order-api.service';
import type { CustomerOrderPage } from './customer-order.model';
import { CustomerOrders } from './customer-orders';

describe('CustomerOrders', () => {
  let fixture: ComponentFixture<CustomerOrders>;
  let element: HTMLElement;
  let result: Subject<CustomerOrderPage>;

  const api = {
    getOrders: vi.fn(),
  };

  const firstPage: CustomerOrderPage = {
    items: [
      {
        id: 'e6c91c08-bf6b-4b4a-93a4-97b90a62b055',
        createdAt: '2026-10-08T08:00:00Z',
        status: 'new',
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
    result = new Subject<CustomerOrderPage>();
    api.getOrders.mockReset();
    api.getOrders.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [CustomerOrders, getTranslocoTestingModule()],
      providers: [provideRouter([]), { provide: CustomerOrderApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerOrders);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(page: CustomerOrderPage): Promise<void> {
    result.next(page);
    result.complete();
    await fixture.whenStable();
  }

  function prepareNextRequest(): void {
    result = new Subject<CustomerOrderPage>();
    api.getOrders.mockReturnValue(result.asObservable());
  }

  function paginationButtons(): HTMLButtonElement[] {
    return Array.from(
      element.querySelectorAll<HTMLButtonElement>('.customer-orders__pagination button'),
    );
  }

  it('should request the first page and show loading', () => {
    expect(api.getOrders).toHaveBeenCalledExactlyOnceWith(0);
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zamówień');
    expect(element.querySelector('.customer-orders__list')).toBeNull();
  });

  it('should display orders with a details link and formatted amount', async () => {
    await respond(firstPage);

    const item = element.querySelector('.customer-orders__item')!;

    expect(item.textContent).toContain(firstPage.items[0].id);
    expect(item.textContent).toContain('Nowe');
    expect(item.textContent?.replace(/\s/g, '')).toContain('29,00zł');
    expect(item.querySelector('a')?.getAttribute('href')).toBe(
      `/pl/account/orders/${firstPage.items[0].id}`,
    );
    expect(item.querySelector('time')?.getAttribute('datetime')).toBe(firstPage.items[0].createdAt);
    expect(element.querySelector('output')).toBeNull();
    expect(element.textContent).toContain('Strona 1 z 2');
  });

  it('should show an empty history and a shop link', async () => {
    await respond({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    expect(element.textContent).toContain('Nie masz jeszcze zamówień złożonych z tego konta.');
    expect(element.querySelector('.customer-orders__list')).toBeNull();
    expect(element.querySelector('.customer-orders__pagination')).toBeNull();
    expect(element.querySelector('a[href="/pl/products"]')).not.toBeNull();
  });

  it('should retry after a loading failure', async () => {
    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać zamówień.',
    );

    prepareNextRequest();
    element.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenCalledTimes(2);
    expect(api.getOrders).toHaveBeenLastCalledWith(0);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    await respond(firstPage);
    expect(element.querySelector('.customer-orders__list')).not.toBeNull();
  });

  it('should paginate and disable controls while loading', async () => {
    await respond(firstPage);

    expect(paginationButtons()[0].disabled).toBe(true);
    expect(paginationButtons()[1].disabled).toBe(false);

    prepareNextRequest();
    paginationButtons()[1].click();
    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(1);
    expect(paginationButtons().every((button) => button.disabled)).toBe(true);

    await respond({ ...firstPage, page: 1 });

    expect(paginationButtons()[0].disabled).toBe(false);
    expect(paginationButtons()[1].disabled).toBe(true);

    prepareNextRequest();
    paginationButtons()[0].click();
    await fixture.whenStable();

    expect(api.getOrders).toHaveBeenLastCalledWith(0);
    await respond(firstPage);
  });

  it('should retain the current page after a temporary pagination failure', async () => {
    await respond(firstPage);

    prepareNextRequest();
    paginationButtons()[1].click();
    await fixture.whenStable();

    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    expect(element.querySelector('.customer-orders__item')?.textContent).toContain(
      firstPage.items[0].id,
    );
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać zamówień.',
    );
    expect(element.textContent).toContain('Strona 1 z 2');
  });

  it('should remove orders and offer sign-in when the session expires', async () => {
    await respond(firstPage);

    prepareNextRequest();
    paginationButtons()[1].click();
    await fixture.whenStable();

    result.error(new HttpErrorResponse({ status: 401 }));
    await fixture.whenStable();

    const alert = element.querySelector('[role="alert"]')!;

    expect(alert.textContent).toContain('Sesja wygasła.');
    expect(alert.querySelector('a')?.getAttribute('href')).toBe('/pl/account/login');
    expect(alert.querySelector('button')).toBeNull();
    expect(element.querySelector('.customer-orders__list')).toBeNull();
    expect(element.querySelector('.customer-orders__pagination')).toBeNull();
  });

  it('should show an access error without displaying orders', async () => {
    result.error(new HttpErrorResponse({ status: 403 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie można wyświetlić historii zamówień',
    );
    expect(element.querySelector('.customer-orders__list')).toBeNull();
  });
});
