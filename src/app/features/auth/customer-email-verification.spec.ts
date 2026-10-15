import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { AuthApiService } from '../../core/auth/auth-api.service';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { CustomerEmailVerification } from './customer-email-verification';

describe('CustomerEmailVerification', () => {
  let fixture: ComponentFixture<CustomerEmailVerification>;
  let element: HTMLElement;
  let fragment: BehaviorSubject<string | null>;
  let result: Subject<void>;

  const token = 'a'.repeat(43);
  const api = {
    verifyEmail: vi.fn(),
  };

  beforeEach(async () => {
    fragment = new BehaviorSubject<string | null>(`token=${token}`);
    result = new Subject<void>();

    api.verifyEmail.mockReset();
    api.verifyEmail.mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [CustomerEmailVerification, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { fragment: fragment.asObservable() },
        },
        { provide: AuthApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerEmailVerification);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  function button(): HTMLButtonElement | null {
    return element.querySelector<HTMLButtonElement>('button');
  }

  it('should wait for confirmation instead of submitting on page load', () => {
    expect(api.verifyEmail).not.toHaveBeenCalled();
    expect(button()?.textContent).toContain('Potwierdź adres e-mail');
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('should submit only once while waiting', async () => {
    button()!.click();
    await fixture.whenStable();

    expect(api.verifyEmail).toHaveBeenCalledExactlyOnceWith(token);
    expect(button()?.disabled).toBe(true);

    button()!.click();
    await fixture.whenStable();

    expect(api.verifyEmail).toHaveBeenCalledTimes(1);
  });

  it('should show success and a sign-in link', async () => {
    button()!.click();

    result.next();
    result.complete();
    await fixture.whenStable();

    expect(element.querySelector('output')?.textContent).toContain(
      'Adres e-mail został potwierdzony.',
    );
    expect(element.querySelector('.button')?.getAttribute('href')).toBe('/pl/account/login');
    expect(button()).toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
  });

  it('should reject a missing token without sending a request', async () => {
    fragment.next(null);
    await fixture.whenStable();

    expect(button()).toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Link jest nieprawidłowy',
    );
    expect(api.verifyEmail).not.toHaveBeenCalled();
  });

  it('should reject a malformed token without sending a request', async () => {
    fragment.next('token=invalid');
    await fixture.whenStable();

    expect(button()).toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Link jest nieprawidłowy',
    );
    expect(api.verifyEmail).not.toHaveBeenCalled();
  });

  it('should stop offering confirmation for an expired or used token', async () => {
    button()!.click();

    result.error(new HttpErrorResponse({ status: 400 }));
    await fixture.whenStable();

    expect(button()).toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'wygasł lub został już wykorzystany',
    );
    expect(element.querySelector('output')).toBeNull();
  });

  it('should allow retrying after a temporary failure', async () => {
    button()!.click();

    result.error(new HttpErrorResponse({ status: 503 }));
    await fixture.whenStable();

    expect(button()?.disabled).toBe(false);
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się potwierdzić adresu.',
    );

    result = new Subject<void>();
    api.verifyEmail.mockReturnValue(result.asObservable());

    button()!.click();
    await fixture.whenStable();

    expect(api.verifyEmail).toHaveBeenCalledTimes(2);
    expect(api.verifyEmail).toHaveBeenLastCalledWith(token);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    result.next();
    result.complete();
    await fixture.whenStable();

    expect(element.querySelector('output')?.textContent).toContain(
      'Adres e-mail został potwierdzony.',
    );
  });
});
