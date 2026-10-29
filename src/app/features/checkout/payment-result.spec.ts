import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  type ParamMap,
  provideRouter,
} from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import {
  PaymentApiService,
  type PaymentStatus,
  type PaymentStatusResult,
} from './payment-api.service';
import { PaymentResult } from './payment-result';
import { DigitalOrderDownloadApiService } from '../digital-downloads/digital-order-download-api.service';

describe('PaymentResult', () => {
  let fixture: ComponentFixture<PaymentResult>;
  let element: HTMLElement;
  let params: BehaviorSubject<ParamMap>;

  const attemptId = '38e96ac8-89e7-40f1-a856-086992226bdf';
  const orderId = '93af42f1-d557-4b3d-b452-24976e049713';

  const downloadsApi = {
    getDownloads: vi.fn(),
  };

  const api = {
    getStatus: vi.fn(),
  };

  beforeEach(async () => {
    params = new BehaviorSubject(
      convertToParamMap({ attempt: attemptId }),
    );

    api.getStatus.mockReset();
    api.getStatus.mockReturnValue(of(result('pending')));

    downloadsApi.getDownloads.mockReset();
    downloadsApi.getDownloads.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [PaymentResult, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: params.asObservable(),
            snapshot: {
              queryParamMap: params.value,
            },
          },
        },
        {
          provide: PaymentApiService,
          useValue: api,
        },
        {
          provide: DigitalOrderDownloadApiService,
          useValue: downloadsApi,
        },
      ],
    }).compileComponents();

    // RxJS timers use setInterval. Other timers remain real.
    vi.useFakeTimers({
      toFake: ['setInterval', 'clearInterval'],
    });

    fixture = TestBed.createComponent(PaymentResult);
    element = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function result(status: PaymentStatus): PaymentStatusResult {
    return {
      paymentAttemptId: attemptId,
      orderId,
      status,
    };
  }

  function advance(milliseconds = 0): void {
    vi.advanceTimersByTime(milliseconds);
    fixture.detectChanges();
  }

  function output(): string {
    return element.querySelector('output')?.textContent ?? '';
  }

  function alert(): string {
    return element.querySelector('[role="alert"]')?.textContent ?? '';
  }

  function button(): HTMLButtonElement | null {
    return element.querySelector('button');
  }

  it('should poll until the backend confirms payment', () => {
    api.getStatus
      .mockReturnValueOnce(of(result('pending')))
      .mockReturnValueOnce(of(result('succeeded')));

    advance();

    expect(api.getStatus).toHaveBeenCalledExactlyOnceWith(attemptId);
    expect(output()).toContain('Płatność nie została jeszcze potwierdzona');

    advance(3000);

    expect(output()).toContain('Płatność testowa została potwierdzona');
    expect(button()).toBeNull();

    advance(30000);

    expect(api.getStatus).toHaveBeenCalledTimes(2);
  });

  it('should ignore success values supplied in the URL', () => {
    params.next(convertToParamMap({
      attempt: attemptId,
      status: 'succeeded',
      payment_status: 'paid',
      session_id: 'cs_test_anything',
    }));

    advance();

    expect(output()).toContain('Płatność nie została jeszcze potwierdzona');
    expect(output()).not.toContain('Płatność testowa została potwierdzona');
  });

  it('should reject a missing or malformed attempt identifier', () => {
    params.next(convertToParamMap({}));
    advance();

    expect(alert()).toContain('prawidłowego identyfikatora');
    expect(api.getStatus).not.toHaveBeenCalled();

    params.next(convertToParamMap({ attempt: 'invalid' }));
    advance();

    expect(alert()).toContain('prawidłowego identyfikatora');
    expect(api.getStatus).not.toHaveBeenCalled();
  });

  it('should show an access error without reporting payment failure', () => {
    api.getStatus.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    advance();

    expect(alert()).toContain('Nie można odczytać tej płatności');
    expect(output()).toBe('');
    expect(button()).not.toBeNull();

    advance(30000);

    expect(api.getStatus).toHaveBeenCalledTimes(1);
  });

  it('should allow retrying after a connection failure', () => {
    api.getStatus.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );

    advance();

    expect(alert()).toContain('Nie udało się sprawdzić statusu');
    expect(button()).not.toBeNull();

    api.getStatus.mockReturnValue(of(result('succeeded')));

    button()!.click();
    advance();

    expect(alert()).toBe('');
    expect(output()).toContain('Płatność testowa została potwierdzona');
    expect(api.getStatus).toHaveBeenCalledTimes(2);
  });

  it('should stop automatic polling after about one minute', () => {
    advance(60000);

    expect(api.getStatus).toHaveBeenCalledTimes(21);
    expect(button()?.textContent).toContain('Sprawdź ponownie');

    advance(30000);

    expect(api.getStatus).toHaveBeenCalledTimes(21);
  });

  it('should avoid overlapping status requests', () => {
    const response = new Subject<PaymentStatusResult>();

    api.getStatus.mockReturnValue(response.asObservable());

    advance();
    advance(6000);

    expect(api.getStatus).toHaveBeenCalledTimes(1);

    response.next(result('succeeded'));
    response.complete();
    fixture.detectChanges();

    expect(output()).toContain('Płatność testowa została potwierdzona');
  });

  it('should stop polling when the page is destroyed', () => {
    advance();

    expect(api.getStatus).toHaveBeenCalledTimes(1);

    fixture.destroy();
    advance(30000);

    expect(api.getStatus).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['failed', 'zakończyła się niepowodzeniem'],
    ['expired', 'wygasła'],
  ] as const)(
    'should display %s and stop polling',
    (status, expectedText) => {
      api.getStatus.mockReturnValue(of(result(status)));

      advance();

      expect(output()).toContain(expectedText);
      expect(button()).toBeNull();

      advance(30000);

      expect(api.getStatus).toHaveBeenCalledTimes(1);
    },
  );

  it('should load downloads after payment is confirmed', () => {
    api.getStatus.mockReturnValue(of(result('succeeded')));

    advance();

    expect(element.querySelector('app-digital-order-downloads')).not.toBeNull();

    expect(downloadsApi.getDownloads).toHaveBeenCalledExactlyOnceWith(orderId);
  });

  it('should not load downloads while payment is pending', () => {
    advance();

    expect(element.querySelector('app-digital-order-downloads')).toBeNull();

    expect(downloadsApi.getDownloads).not.toHaveBeenCalled();
  });
});
