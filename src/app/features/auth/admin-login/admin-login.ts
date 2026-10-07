import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { email, form, FormField, required } from '@angular/forms/signals';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../../core/auth/auth-session.service';

@Component({
  selector: 'app-admin-login',
  imports: [FormField, TranslocoPipe],
  templateUrl: './admin-login.html',
  styleUrl: './admin-login.scss',
})
export class AdminLogin {
  protected readonly session = inject(AuthSessionService);

  private readonly destroyRef = inject(DestroyRef);

  protected readonly isSubmitting = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly loginModel = signal({
    email: '',
    password: '',
  });

  protected readonly loginForm = form(this.loginModel, (path) => {
    required(path.email, {
      message: 'auth.errors.emailRequired',
    });

    email(path.email, {
      message: 'auth.errors.emailInvalid',
    });

    required(path.password, {
      message: 'auth.errors.passwordRequired',
    });
  });

  constructor() {
  this.session
    .refresh()
    .pipe(takeUntilDestroyed(this.destroyRef))
    .subscribe({
      error: () => {
        this.errorKey.set('auth.errors.sessionUnavailable');
      },
    });
}

  protected submit(event: Event): void {
    event.preventDefault();

    if (this.isSubmitting()) {
      return;
    }

    this.loginForm.email().markAsTouched();
    this.loginForm.password().markAsTouched();
    this.errorKey.set(null);

    if (this.loginForm().invalid()) {
      return;
    }

    this.isSubmitting.set(true);

    this.session
      .login(this.loginModel())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: (user) => {
          this.clearPassword();

          if (!user.roles.includes('ADMIN')) {
            this.errorKey.set('auth.errors.adminRequired');
          }
        },
        error: (error: unknown) => {
          this.clearPassword();

          this.errorKey.set(
            error instanceof HttpErrorResponse && error.status === 401
              ? 'auth.errors.invalidCredentials'
              : 'auth.errors.loginUnavailable',
          );
        },
      });
  }

  protected logout(): void {
    if (this.isSubmitting()) {
      return;
    }
  
    this.isSubmitting.set(true);
    this.errorKey.set(null);
  
    this.session
      .logout()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        error: () => {
          this.errorKey.set('auth.errors.logoutUnavailable');
        },
      });
  }

  private clearPassword(): void {
    this.loginModel.update((model) => ({
      ...model,
      password: '',
    }));
  }
}
