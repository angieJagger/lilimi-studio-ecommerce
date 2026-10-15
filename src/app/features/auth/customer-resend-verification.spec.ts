import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerResendVerification } from './customer-resend-verification';

describe('CustomerResendVerification', () => {
  let fixture: ComponentFixture<CustomerResendVerification>;
  let element: HTMLElement;
  let result: Subject<void>;

  const api = {
    resendVerification: vi.fn(),
  };

  beforeEach(async () => {
    result = new Subject<void>();

    api.resendVerification.mockReset();
    api.resendVerification.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [
        CustomerResendVerification,
        getTranslocoTestingModule(),
      ],
      providers: [
        provideRouter([]),
        { provide: AuthApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerResendVerification);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function fillEmail(value: string): Promise<void> {
    const input = element.querySelector<HTMLInputElement>('#resend-email')!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    await fixture.whenStable();
  }

  it('should reject an empty email', async () => {
    await submit();

    expect(api.resendVerification).not.toHaveBeenCalled();
    expect(element.querySelector('#resend-email')?.getAttribute('aria-invalid'))
      .toBe('true');
    expect(element.textContent).toContain('Podaj poprawny adres e-mail.');
  });

  it('should reject an invalid email', async () => {
    await fillEmail('invalid-email');
    await submit();

    expect(api.resendVerification).not.toHaveBeenCalled();
    expect(element.textContent).toContain('Podaj poprawny adres e-mail.');
  });

  it('should submit once and disable the form while waiting', async () => {
    await fillEmail('customer@example.com');
    await submit();
    await submit();

    expect(api.resendVerification).toHaveBeenCalledExactlyOnceWith(
      'customer@example.com',
      'pl',
    );
    expect(element.querySelector<HTMLInputElement>('input')?.disabled).toBe(true);
    expect(element.querySelector<HTMLButtonElement>('button')?.disabled).toBe(true);
  });

  it('should show a neutral confirmation and remove the form', async () => {
    await fillEmail('customer@example.com');
    await submit();

    result.next();
    result.complete();
    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent)
      .toContain('Jeśli konto wymaga potwierdzenia');
    expect(element.querySelector('a')?.getAttribute('href'))
      .toBe('/pl/account/login');
  });

  it('should preserve email and allow retry after a failure', async () => {
    await fillEmail('customer@example.com');
    await submit();

    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent)
      .toContain('Nie udało się obsłużyć żądania.');
    expect(element.querySelector<HTMLInputElement>('input')?.value)
      .toBe('customer@example.com');
    expect(element.querySelector<HTMLInputElement>('input')?.disabled).toBe(false);

    result = new Subject<void>();
    api.resendVerification.mockReturnValue(result.asObservable());

    await submit();

    expect(api.resendVerification).toHaveBeenCalledTimes(2);

    result.next();
    result.complete();
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('output')).not.toBeNull();
  });
});
