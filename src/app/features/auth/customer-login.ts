import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-customer-login',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './customer-login.html',
  styleUrl: './customer-login.scss',
})
export class CustomerLogin {
  protected readonly session = inject(AuthSessionService);
  protected readonly transloco = inject(TranslocoService);

  private readonly formBuilder = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly isLoadingSession = signal(true);
  protected readonly isSubmitting = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly loginForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', Validators.required],
  });

  constructor() {
    this.session
      .refresh()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoadingSession.set(false)),
      )
      .subscribe({
        error: () => {
          this.errorKey.set('customerLogin.errors.sessionUnavailable');
        },
      });
  }

  protected submit(): void {
    if (this.isLoadingSession() || this.isSubmitting() || this.session.isAuthenticated()) {
      return;
    }

    this.errorKey.set(null);
    this.loginForm.markAllAsTouched();

    if (this.loginForm.invalid) {
      return;
    }

    const credentials = this.loginForm.getRawValue();

    this.isSubmitting.set(true);
    this.loginForm.disable({ emitEvent: false });

    this.session
      .login(credentials)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isSubmitting.set(false);
          this.loginForm.enable({ emitEvent: false });
        }),
      )
      .subscribe({
        next: () => {
          this.loginForm.controls.password.reset();
        },
        error: (error: unknown) => {
          this.loginForm.controls.password.reset();

          this.errorKey.set(
            this.loginError(error),
          );
        },
      });
  }

  private loginError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 429) return 'customerLogin.errors.tooManyAttempts';
      if (error.status === 401) return 'customerLogin.errors.invalidCredentials';
    }
    return 'customerLogin.errors.loginUnavailable';
  }

  protected logout(): void {
    if (this.isLoadingSession() || this.isSubmitting()) {
      return;
    }

    this.errorKey.set(null);
    this.isSubmitting.set(true);

    this.session
      .logout()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: () => {
          this.loginForm.reset();
        },
        error: () => {
          this.errorKey.set('customerLogin.errors.logoutUnavailable');
        },
      });
  }
}
