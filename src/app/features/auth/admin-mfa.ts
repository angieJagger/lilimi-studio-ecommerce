import { isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, PLATFORM_ID, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize, type Observable } from 'rxjs';
import { AdminMfaApiService } from '../../core/auth/admin-mfa-api.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-admin-mfa',
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './admin-mfa.html',
  styleUrl: './customer-login.scss',
})
export class AdminMfa {
  private readonly api = inject(AdminMfaApiService);
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly enabled = signal(false);
  protected readonly secret = signal<string | null>(null);
  protected readonly recoveryMode = signal(false);
  protected readonly recoveryCodes = signal<readonly string[]>([]);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(64)]],
  });

  constructor() {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.api
      .getStatus()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (status) => {
          this.enabled.set(status.enabled);

          if (status.verified) {
            this.enterPanel();
          }
        },
        error: (error: unknown) => this.handleError(error),
      });

    this.destroyRef.onDestroy(() => {
      this.secret.set(null);
      this.recoveryCodes.set([]);
      this.form.reset();
    });
  }

  protected startSetup(): void {
    this.run(this.api.beginSetup(), (result) => {
      this.secret.set(result.secret);
    });
  }

  protected submit(): void {
    if (this.loading() || this.busy()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const code = this.form.getRawValue().code.trim();

    if (!this.enabled()) {
      if (!this.secret()) {
        return;
      }

      this.run(this.api.activate(code), (result) => {
        this.secret.set(null);
        this.form.reset();
        this.enabled.set(true);
        this.recoveryCodes.set(result.recoveryCodes);
      });

      return;
    }

    const request = this.recoveryMode() ? this.api.recover(code) : this.api.verify(code);

    this.run(request, () => {
      this.form.reset();
      this.enterPanel();
    });
  }

  protected toggleRecovery(): void {
    if (this.busy()) {
      return;
    }

    this.recoveryMode.update((value) => !value);
    this.errorKey.set(null);
    this.form.reset();
  }

  protected continueAfterSavingCodes(): void {
    this.recoveryCodes.set([]);
    this.enterPanel();
  }

  protected logout(): void {
    this.run(this.session.logout(), () => {
      void this.router.navigate(['/', this.language(), 'account', 'login']);
    });
  }

  private enterPanel(): void {
    this.run(this.session.refresh(), (user) => {
      if (user?.roles.includes('ADMIN')) {
        void this.router.navigate(['/', this.language(), 'admin']);
      } else {
        this.errorKey.set('adminMfa.errors.unavailable');
      }
    });
  }

  private run<T>(request: Observable<T>, onSuccess: (result: T) => void): void {
    if (this.busy()) {
      return;
    }

    this.busy.set(true);
    this.errorKey.set(null);

    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          onSuccess(result);
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  private handleError(error: unknown): void {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        void this.router.navigate(['/', this.language(), 'account', 'login']);
        return;
      }

      if (error.status === 429) {
        this.errorKey.set('adminMfa.errors.tooManyAttempts');
        return;
      }

      if (error.status === 400) {
        this.form.controls.code.reset();
        this.errorKey.set('adminMfa.errors.invalidCode');
        return;
      }
    }

    this.errorKey.set('adminMfa.errors.unavailable');
  }

  private language(): 'pl' | 'en' {
    return this.transloco.getActiveLang() === 'en' ? 'en' : 'pl';
  }
}
