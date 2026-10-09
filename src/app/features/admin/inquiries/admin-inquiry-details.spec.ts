import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminInquiryApiService } from './admin-inquiry-api.service';
import { AdminInquiryDetails } from './admin-inquiry-details';
import type { AdminInquiryDetails as InquiryDetails } from './admin-inquiry.model';

describe('AdminInquiryDetails', () => {
  let fixture: ComponentFixture<AdminInquiryDetails>;
  let element: HTMLElement;
  let result: Subject<InquiryDetails>;
  let statusResult: Subject<InquiryDetails>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const api = {
    getInquiry: vi.fn(),
    changeStatus: vi.fn(),
  };

  const inquiry: InquiryDetails = {
    id: '1548c928-33de-4c58-98ea-044517b0bd75',
    version: 0,
    createdAt: '2026-10-09T08:00:00Z',
    status: 'new',
    language: 'pl',
    name: 'Anna Kowalska',
    email: 'anna@example.com',
    projectType: 'embroideredProduct',
    description: 'Proszę o wycenę haftu.\nPotrzebuję dwóch bluz.',
    inspirationUrl: 'https://example.com/inspiration',
    productId: 'embroidered-002',
  };

  beforeEach(async () => {
    result = new Subject<InquiryDetails>();
    statusResult = new Subject<InquiryDetails>();
    params = new BehaviorSubject(convertToParamMap({ id: inquiry.id }));

    api.getInquiry.mockReset();
    api.getInquiry.mockReturnValue(result.asObservable());
    api.changeStatus.mockReset();
    api.changeStatus.mockReturnValue(statusResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminInquiryDetails, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: params.asObservable() },
        },
        { provide: AdminInquiryApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminInquiryDetails);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(details: InquiryDetails): Promise<void> {
    result.next(details);
    result.complete();

    await fixture.whenStable();
  }

  async function fail(status: number): Promise<void> {
    result.error(new HttpErrorResponse({ status }));

    await fixture.whenStable();
  }

  it('should request the inquiry from the route and show loading', () => {
    expect(api.getInquiry).toHaveBeenCalledExactlyOnceWith(inquiry.id);
    expect(element.querySelector('output')).not.toBeNull();
    expect(element.querySelector('.inquiry-details__card')).toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('should display inquiry details and a link back to the list', async () => {
    await respond(inquiry);

    expect(element.textContent).toContain(inquiry.id);
    expect(element.textContent).toContain(inquiry.name);
    expect(element.textContent).toContain(inquiry.email);
    expect(element.textContent).toContain(inquiry.productId);
    expect(element.textContent).toContain('Nowe');
    expect(element.textContent).toContain('Polski');

    expect(element.querySelector('.inquiry-details__description')?.textContent).toBe(
      inquiry.description,
    );

    expect(element.querySelector('.inquiry-details__inspiration')?.textContent).toBe(
      inquiry.inspirationUrl,
    );

    expect(element.querySelector('time')?.getAttribute('datetime')).toBe(inquiry.createdAt);

    expect(element.querySelector('.inquiry-details__back')?.getAttribute('href')).toBe(
      '/pl/admin/inquiries',
    );

    expect(element.querySelector('output')).toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('should show a missing inquiry message', async () => {
    await fail(404);

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie znaleziono zapytania.',
    );

    expect(element.querySelector('.inquiry-details__card')).toBeNull();
    expect(element.querySelector('output')).toBeNull();
  });

  it('should retry after a connection error and display the result', async () => {
    await fail(0);

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać szczegółów zapytania.',
    );

    result = new Subject<InquiryDetails>();
    api.getInquiry.mockReturnValue(result.asObservable());

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getInquiry).toHaveBeenCalledTimes(2);
    expect(api.getInquiry).toHaveBeenLastCalledWith(inquiry.id);
    expect(element.querySelector('output')).not.toBeNull();

    await respond(inquiry);

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.textContent).toContain(inquiry.name);
  });

  it.each([401, 403])('should show a login link for HTTP %s', async (status) => {
    await fail(status);

    expect(element.querySelector('[role="alert"]')).not.toBeNull();

    expect(element.querySelector('a[href="/pl/admin/login"]')).not.toBeNull();

    expect(element.querySelector('button')).toBeNull();
    expect(element.querySelector('.inquiry-details__card')).toBeNull();
  });

  it('should ignore the previous request when the route changes', async () => {
    const previousResult = result;
    const nextInquiry: InquiryDetails = {
      ...inquiry,
      id: '6c87a629-9bf0-452b-b4a0-b1c94c056731',
      name: 'Maria Nowak',
      email: 'maria@example.com',
    };

    result = new Subject<InquiryDetails>();
    api.getInquiry.mockReturnValue(result.asObservable());

    params.next(convertToParamMap({ id: nextInquiry.id }));

    await fixture.whenStable();

    previousResult.next(inquiry);
    previousResult.complete();

    await fixture.whenStable();

    expect(api.getInquiry).toHaveBeenLastCalledWith(nextInquiry.id);
    expect(element.querySelector('output')).not.toBeNull();
    expect(element.querySelector('.inquiry-details__card')).toBeNull();

    await respond(nextInquiry);

    expect(element.textContent).toContain('Maria Nowak');
    expect(element.textContent).not.toContain('Anna Kowalska');
  });

    it('should allow cancelling a status change without sending a request', async () => {
      await respond(inquiry);

      element.querySelector<HTMLButtonElement>('[data-status="in_progress"]')!.click();

      await fixture.whenStable();

      expect(element.querySelector('[data-testid="confirm-status"]')).not.toBeNull();

      expect(api.changeStatus).not.toHaveBeenCalled();

      element.querySelector<HTMLButtonElement>('[data-testid="cancel-status"]')!.click();

      await fixture.whenStable();

      expect(element.querySelector('[data-testid="confirm-status"]')).toBeNull();

      expect(element.querySelector('[data-status="in_progress"]')).not.toBeNull();

      expect(api.changeStatus).not.toHaveBeenCalled();
    });

    it('should send the current version and display the updated status', async () => {
      await respond(inquiry);

      element.querySelector<HTMLButtonElement>('[data-status="in_progress"]')!.click();

      await fixture.whenStable();

      element.querySelector<HTMLButtonElement>('[data-testid="confirm-status"]')!.click();

      await fixture.whenStable();

      expect(api.changeStatus).toHaveBeenCalledExactlyOnceWith(inquiry.id, {
        status: 'in_progress',
        expectedVersion: inquiry.version,
      });

      statusResult.next({
        ...inquiry,
        status: 'in_progress',
        version: inquiry.version + 1,
      });
      statusResult.complete();

      await fixture.whenStable();

      expect(element.querySelector('.inquiry-details__status strong')?.textContent).toContain(
        'W trakcie',
      );

      expect(element.querySelector('[data-testid="confirm-status"]')).toBeNull();

      expect(element.querySelector('[data-status="answered"]')).not.toBeNull();
    });

    it('should prevent duplicate submissions while saving', async () => {
      await respond(inquiry);

      element.querySelector<HTMLButtonElement>('[data-status="in_progress"]')!.click();

      await fixture.whenStable();

      const confirmButton = element.querySelector<HTMLButtonElement>(
        '[data-testid="confirm-status"]',
      )!;

      confirmButton.click();
      confirmButton.click();

      await fixture.whenStable();

      expect(api.changeStatus).toHaveBeenCalledTimes(1);
      expect(confirmButton.disabled).toBe(true);

      expect(
        element.querySelector<HTMLButtonElement>('[data-testid="cancel-status"]')!.disabled,
      ).toBe(true);

      statusResult.next({
        ...inquiry,
        status: 'in_progress',
        version: inquiry.version + 1,
      });
      statusResult.complete();

      await fixture.whenStable();
    });

    it('should require reloading after a version conflict', async () => {
      await respond(inquiry);

      element.querySelector<HTMLButtonElement>('[data-status="in_progress"]')!.click();

      await fixture.whenStable();

      element.querySelector<HTMLButtonElement>('[data-testid="confirm-status"]')!.click();

      await fixture.whenStable();

      statusResult.error(
        new HttpErrorResponse({
          status: 409,
          error: { code: 'INQUIRY_VERSION_CONFLICT' },
        }),
      );

      await fixture.whenStable();

      expect(element.querySelector('[role="alert"]')?.textContent).toContain(
        'Zapytanie zostało zmienione.',
      );

      expect(element.querySelector('[data-status]')).toBeNull();
      expect(api.changeStatus).toHaveBeenCalledTimes(1);

      result = new Subject<InquiryDetails>();
      api.getInquiry.mockReturnValue(result.asObservable());

      element.querySelector<HTMLButtonElement>('.inquiry-details__status button')!.click();

      await fixture.whenStable();

      expect(api.getInquiry).toHaveBeenCalledTimes(2);

      await respond({
        ...inquiry,
        status: 'in_progress',
        version: inquiry.version + 1,
      });

      expect(element.querySelector('[role="alert"]')).toBeNull();

      expect(element.querySelector('[data-status="answered"]')).not.toBeNull();
    });

      it('should require reloading after an unconfirmed status update', async () => {
        await respond(inquiry);

        element.querySelector<HTMLButtonElement>('[data-status="in_progress"]')!.click();

        await fixture.whenStable();

        element.querySelector<HTMLButtonElement>('[data-testid="confirm-status"]')!.click();

        await fixture.whenStable();

        statusResult.error(
          new HttpErrorResponse({
            status: 0,
            statusText: 'Unknown Error',
          }),
        );

        await fixture.whenStable();

        expect(element.querySelector('[role="alert"]')?.textContent).toContain(
          'Nie udało się potwierdzić zmiany statusu.',
        );

        expect(element.querySelector('.inquiry-details__status strong')?.textContent).toContain(
          'Nowe',
        );

        expect(element.querySelector('[data-status]')).toBeNull();
        expect(api.changeStatus).toHaveBeenCalledTimes(1);

        result = new Subject<InquiryDetails>();
        api.getInquiry.mockReturnValue(result.asObservable());

        element.querySelector<HTMLButtonElement>('.inquiry-details__status button')!.click();

        await fixture.whenStable();

        await respond({
          ...inquiry,
          status: 'in_progress',
          version: inquiry.version + 1,
        });

        expect(element.querySelector('[role="alert"]')).toBeNull();

        expect(element.querySelector('.inquiry-details__status strong')?.textContent).toContain(
          'W trakcie',
        );

        expect(api.changeStatus).toHaveBeenCalledTimes(1);
      });

      it('should not offer status changes for a closed inquiry', async () => {
        await respond({
          ...inquiry,
          status: 'closed',
        });

        expect(element.querySelector('.inquiry-details__status')?.textContent).toContain(
          'Zapytanie jest zamknięte.',
        );

        expect(element.querySelector('[data-status]')).toBeNull();

        expect(element.querySelector('[data-testid="confirm-status"]')).toBeNull();

        expect(api.changeStatus).not.toHaveBeenCalled();
      });
});
