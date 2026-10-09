import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import type { AdminInquiryPage } from './admin-inquiry.model';
import { AdminInquiries } from './admin-inquiries';

describe('AdminInquiries', () => {
  let fixture: ComponentFixture<AdminInquiries>;
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
      providers: [provideRouter([]), { provide: AdminInquiryApiService, useValue: api }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminInquiries);
    element = fixture.nativeElement;

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
    expect(api.getInquiries).toHaveBeenCalledExactlyOnceWith(0);
    expect(element.querySelector('output')?.textContent).toContain('Ładowanie zapytań');
    expect(element.querySelector('table')).toBeNull();
  });

  it('should display contact details, project type and status', async () => {
    await respond(firstPage);

    const row = element.querySelector('tbody tr')!;

    expect(row.textContent).toContain('Anna Kowalska');
    expect(row.textContent).toContain('anna@example.com');
    expect(row.textContent).toContain(firstPage.items[0].id);
    expect(row.textContent).toContain('Strona internetowa');
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

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenCalledTimes(2);
    expect(api.getInquiries).toHaveBeenLastCalledWith(0);

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

  it('should load the next and previous pages', async () => {
    await respond(firstPage);

    expect(paginationButtons()[0].disabled).toBe(true);
    expect(paginationButtons()[1].disabled).toBe(false);

    result = new Subject<AdminInquiryPage>();
    api.getInquiries.mockReturnValue(result.asObservable());

    paginationButtons()[1].click();

    await fixture.whenStable();

    expect(api.getInquiries).toHaveBeenLastCalledWith(1);

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

    expect(api.getInquiries).toHaveBeenLastCalledWith(0);

    await respond(firstPage);

    expect(element.textContent).toContain('Strona 1 z 2');
  });
});
