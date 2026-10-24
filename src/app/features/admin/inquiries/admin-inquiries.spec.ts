import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type { AdminInquiryPage } from './admin-inquiry.model';
import { AdminInquiries } from './admin-inquiries';

describe('AdminInquiries', () => {
  let fixture: ComponentFixture<unknown>;
  let router: Router;
  let element: HTMLElement;
  let result: Subject<AdminInquiryPage>;

  const api = {
    getInquiries: vi.fn(),
  };

  const firstPage: AdminInquiryPage = {
    items: [
      {
        id: '1548c928-f278-4cbb-b8d0-d57455c72f91',
        createdAt: '2026-10-09T08:00:00Z',
        status: 'new',
        name: 'Anna Kowalska',
        email: 'anna@example.com',
        projectType: 'website',
        productId: null,
      },
    ],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
  };

  beforeEach(async () => {
    result = new Subject<AdminInquiryPage>();

    api.getInquiries.mockReset();
    api.getInquiries.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminInquiries, getTranslocoTestingModule()],
      providers: [
        { provide: AdminInquiryApiService, useValue: api },
        provideRouter([
          {
            path: 'pl/admin/inquiries',
            component: AdminInquiries,
          },
        ]),
      ],
    }).compileComponents();

    router = TestBed.inject(Router);

    const harness = await RouterTestingHarness.create('/pl/admin/inquiries');

    fixture = harness.fixture;
    element = harness.routeNativeElement!;

    await fixture.whenStable();
  });

  async function respond(page: AdminInquiryPage): Promise<void> {
    result.next(page);
    result.complete();

    await fixture.whenStable();
  }

  function paginationButtons(): HTMLButtonElement[] {
    return Array.from(element.querySelectorAll<HTMLButtonElement>('.inquiries-pagination button'));
  }

  it('should request the first page and show loading', () => {
    expect(api.getInquiries).toHaveBeenCalledExactlyOnceWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zapytań');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should display contact details, project type and status', async () => {
    await respond(firstPage);

    const row = element.querySelector('tbody tr')!;

    expect(row.textContent).toContain('Anna Kowalska');
    expect(row.textContent).toContain('anna@example.com');
    expect(row.textContent).toContain(firstPage.items[0].id);
    expect(row.textContent).toContain('Web development');
    expect(row.textContent).toContain('Nowe');

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

    expect(element.textContent).toContain('Brak zapytań do wyświetlenia.');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('.inquiries-pagination')).toBeNull();
  });

  it('should show an error and retry loading', async () => {
    result.error(
      new HttpErrorResponse({
        status: 503,
        statusText: 'Service Unavailable',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać zapytań.',
    );

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('.inquiries-page > button')!.click();

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenCalledTimes(2);
    expect(api.getInquiries).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );

    await respond(firstPage);

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('table')).not.toBeNull();
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
    expect(element.querySelector('a')?.getAttribute('href')).toBe('/pl/admin/login');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should restore filters and page from the address', async () => {
    await respond(firstPage);

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    await router.navigateByUrl(
      '/pl/admin/inquiries?view=answered&status=answered' +
        '&projectType=website&dateFrom=2026-10-01' +
        '&dateTo=2026-10-22&search=Kowalska&page=2',
    );

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenLastCalledWith(2, 20, {
      view: 'answered',
      status: 'answered',
      projectType: 'website',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-22',
      search: 'Kowalska',
    });

    await respond({
      ...firstPage,
      items: [
        {
          ...firstPage.items[0],
          status: 'answered',
        },
      ],
      page: 2,
      totalElements: 60,
      totalPages: 3,
    });

    expect(element.querySelector<HTMLSelectElement>('#inquiry-status-filter')?.value).toBe(
      'answered',
    );

    expect(element.querySelector<HTMLSelectElement>('#inquiry-project-type-filter')?.value).toBe(
      'website',
    );

    expect(element.querySelector<HTMLInputElement>('#inquiry-search')?.value).toBe('Kowalska');

    expect(element.querySelector<HTMLInputElement>('#inquiry-date-from')?.value).toBe('2026-10-01');

    expect(element.querySelector<HTMLInputElement>('#inquiry-date-to')?.value).toBe('2026-10-22');

    const link = element.querySelector<HTMLAnchorElement>('.inquiry-reference a')!;
    const url = new URL(link.href);
    const params = url.searchParams;

    expect(url.pathname).toBe(`/pl/admin/inquiries/${firstPage.items[0].id}`);
    expect(params.get('view')).toBe('answered');
    expect(params.get('status')).toBe('answered');
    expect(params.get('projectType')).toBe('website');
    expect(params.get('dateFrom')).toBe('2026-10-01');
    expect(params.get('dateTo')).toBe('2026-10-22');
    expect(params.get('search')).toBe('Kowalska');
    expect(params.get('page')).toBe('2');
  });

  it('should apply service and date filters and reset the page', async () => {
    await respond(firstPage);

    await router.navigateByUrl('/pl/admin/inquiries?view=all&page=1');
    await fixture.whenStable();

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    element.querySelector<HTMLSelectElement>('#inquiry-status-filter')!.value = 'in_progress';
    element.querySelector<HTMLSelectElement>('#inquiry-project-type-filter')!.value = 'digitizing';
    element.querySelector<HTMLInputElement>('#inquiry-date-from')!.value = '2026-10-01';
    element.querySelector<HTMLInputElement>('#inquiry-date-to')!.value = '2026-10-22';

    element
      .querySelector<HTMLFormElement>('.inquiry-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
      status: 'in_progress',
      projectType: 'digitizing',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-22',
    });

    expect(api.getInquiries).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: 'in_progress',
      projectType: 'digitizing',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-22',
      search: undefined,
    });
  });

  it('should search all inquiries and clear restrictions for a new phrase', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/inquiries?view=archive&status=closed' +
        '&projectType=website&dateFrom=2026-10-01&page=2',
    );
    await fixture.whenStable();

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    element.querySelector<HTMLInputElement>('#inquiry-search')!.value = '  Kowalska  ';

    element
      .querySelector<HTMLFormElement>('.inquiry-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
      search: 'Kowalska',
    });

    expect(api.getInquiries).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: undefined,
      projectType: undefined,
      dateFrom: undefined,
      dateTo: undefined,
      search: 'Kowalska',
    });
  });

  it('should narrow results without clearing the same search phrase', async () => {
    await respond(firstPage);

    await router.navigateByUrl('/pl/admin/inquiries?view=all&search=Kowalska&page=1');
    await fixture.whenStable();

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    element.querySelector<HTMLSelectElement>('#inquiry-project-type-filter')!.value = 'uxUi';

    element
      .querySelector<HTMLFormElement>('.inquiry-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'all',
      projectType: 'uxUi',
      search: 'Kowalska',
    });

    expect(api.getInquiries).toHaveBeenLastCalledWith(0, 20, {
      view: 'all',
      status: undefined,
      projectType: 'uxUi',
      dateFrom: undefined,
      dateTo: undefined,
      search: 'Kowalska',
    });
  });

  it('should clear filters while preserving the view', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/inquiries?view=answered&status=answered' +
        '&projectType=website&dateFrom=2026-10-01' +
        '&dateTo=2026-10-22&search=Kowalska&page=2',
    );
    await fixture.whenStable();

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('.filter-actions button[type="button"]')!.click();

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'answered',
    });

    expect(api.getInquiries).toHaveBeenLastCalledWith(0, 20, {
      view: 'answered',
      status: undefined,
      projectType: undefined,
      dateFrom: undefined,
      dateTo: undefined,
      search: undefined,
    });

    expect(element.querySelector<HTMLInputElement>('#inquiry-search')?.value).toBe('');

    expect(element.querySelector<HTMLSelectElement>('#inquiry-project-type-filter')?.value).toBe(
      '',
    );
  });

  it('should reject a reversed date range without sending a request', async () => {
    await respond(firstPage);

    element.querySelector<HTMLInputElement>('#inquiry-date-from')!.value = '2026-10-22';
    element.querySelector<HTMLInputElement>('#inquiry-date-to')!.value = '2026-10-21';

    element
      .querySelector<HTMLFormElement>('.inquiry-filters')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Data początkowa nie może być późniejsza',
    );
    expect(api.getInquiries).toHaveBeenCalledTimes(1);
    expect(router.url).toBe('/pl/admin/inquiries');
  });

  it('should change view while preserving service, dates and search', async () => {
    await respond(firstPage);

    await router.navigateByUrl(
      '/pl/admin/inquiries?view=active&status=in_progress' +
        '&projectType=website&dateFrom=2026-10-01' +
        '&dateTo=2026-10-22&search=Kowalska&page=1',
    );
    await fixture.whenStable();

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    const buttons = element.querySelectorAll<HTMLButtonElement>('.inquiry-views button');

    buttons[1].click();

    await fixture.whenStable();

    expect(router.parseUrl(router.url).queryParams).toEqual({
      view: 'answered',
      projectType: 'website',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-22',
      search: 'Kowalska',
    });

    expect(api.getInquiries).toHaveBeenLastCalledWith(0, 20, {
      view: 'answered',
      status: undefined,
      projectType: 'website',
      dateFrom: '2026-10-01',
      dateTo: '2026-10-22',
      search: 'Kowalska',
    });

    expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
    expect(buttons[1].getAttribute('aria-pressed')).toBe('true');

    expect(element.querySelector<HTMLSelectElement>('#inquiry-status-filter')?.value).toBe('');
  });

  it('should ignore an older response after filters change', async () => {
    const previousResult = result;

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    await router.navigateByUrl('/pl/admin/inquiries?view=archive');
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
    expect(element.textContent).toContain('Brak zapytań do wyświetlenia.');
  });

  it.each([
    { read: false, unread: true },
    { read: true, unread: false },
    { read: undefined, unread: false },
  ])('should display unread state for read=$read', async ({ read, unread }) => {
    await respond({
      ...firstPage,
      items: [
        {
          ...firstPage.items[0],
          read,
        },
      ],
    });

    const row = element.querySelector<HTMLTableRowElement>('tbody tr')!;
    const link = row.querySelector<HTMLAnchorElement>('.inquiry-reference a')!;
    const indicator = link.querySelector('.unread-indicator');

    expect(row.classList.contains('is-unread')).toBe(unread);
    expect(indicator !== null).toBe(unread);

    if (unread) {
      expect(indicator?.getAttribute('aria-hidden')).toBe('true');
      expect(link.querySelector('.visually-hidden')?.textContent).toContain(
        'Nieprzeczytane zapytanie.',
      );
    } else {
      expect(link.querySelector('.visually-hidden')).toBeNull();
    }

    expect(row.querySelector('.customer-name')?.textContent).toContain(firstPage.items[0].name);
    expect(row.querySelector('.inquiry-status')?.textContent).toContain('Nowe');
  });

  it('should load the next and previous pages', async () => {
    await respond(firstPage);

    expect(paginationButtons()[0].disabled).toBe(true);
    expect(paginationButtons()[1].disabled).toBe(false);

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    paginationButtons()[1].click();

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenLastCalledWith(
      1,
      20,
      expect.objectContaining({ view: 'active' }),
    );

    await respond({
      ...firstPage,
      page: 1,
    });

    expect(paginationButtons()[0].disabled).toBe(false);
    expect(paginationButtons()[1].disabled).toBe(true);
    expect(element.textContent).toContain('Strona 2 z 2');

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    paginationButtons()[0].click();

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenLastCalledWith(
      0,
      20,
      expect.objectContaining({ view: 'active' }),
    );

    await respond(firstPage);

    expect(element.textContent).toContain('Strona 1 z 2');
  });
});
