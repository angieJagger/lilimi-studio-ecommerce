import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { DigitalDownloadLinkApiService } from './digital-download-link-api.service';
import { ResendDigitalDownloadLink } from './resend-digital-download-link';

describe('ResendDigitalDownloadLink', () => {
  let fixture: ComponentFixture<ResendDigitalDownloadLink>;
  let element: HTMLElement;
  let response: Subject<void>;

  const orderId = '93af42f1-d557-4b3d-b452-24976e049713';

  const api = {
    resend: vi.fn(),
  };

  beforeEach(async () => {
    response = new Subject<void>();

    api.resend.mockReset();
    api.resend.mockReturnValue(response.asObservable());

    await TestBed.configureTestingModule({
      imports: [ResendDigitalDownloadLink, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: DigitalDownloadLinkApiService,
          useValue: api,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResendDigitalDownloadLink);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  it('should reject an empty form without calling the API', async () => {
    await submit();

    expect(api.resend).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Podaj prawidłowy numer zamówienia');
    expect(element.textContent).toContain('Podaj prawidłowy adres e-mail');
  });

  it('should trim values before requesting a new link', async () => {
    await fill('#resend-order-id', ` ${orderId} `);
    await fill('#resend-email', ' customer@example.com ');
    await submit();

    expect(api.resend).toHaveBeenCalledExactlyOnceWith(orderId, 'customer@example.com');
  });

  it('should prevent duplicate requests while submitting', async () => {
    await fill('#resend-order-id', orderId);
    await fill('#resend-email', 'customer@example.com');

    await submit();
    await submit();

    expect(api.resend).toHaveBeenCalledTimes(1);
    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
  });

  it('should show a generic confirmation without claiming delivery', async () => {
    await fill('#resend-order-id', orderId);
    await fill('#resend-email', 'customer@example.com');
    await submit();

    response.next();
    response.complete();

    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('[role="status"]')?.textContent).toContain('Jeśli dane pasują');
    expect(element.textContent).not.toContain('customer@example.com');
  });

  it('should show a rate-limit message and preserve entered values', async () => {
    await fill('#resend-order-id', orderId);
    await fill('#resend-email', 'customer@example.com');
    await submit();

    response.error(
      new HttpErrorResponse({
        status: 429,
        headers: new HttpHeaders({
          'Retry-After': '120',
        }),
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Zbyt wiele prób');

    expect(element.querySelector<HTMLInputElement>('#resend-order-id')?.value).toBe(orderId);

    expect(element.querySelector<HTMLInputElement>('#resend-email')?.value).toBe(
      'customer@example.com',
    );

    expect(element.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });

  it('should allow retry after a connection failure', async () => {
    await fill('#resend-order-id', orderId);
    await fill('#resend-email', 'customer@example.com');
    await submit();

    response.error(new HttpErrorResponse({ status: 0 }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się zlecić wysyłki',
    );

    response = new Subject<void>();
    api.resend.mockReturnValue(response.asObservable());

    await submit();

    expect(api.resend).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    response.next();
    response.complete();

    await fixture.whenStable();

    expect(element.querySelector('[role="status"]')).not.toBeNull();
  });

  async function fill(selector: string, value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>(selector)!;

    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();
  }
});
