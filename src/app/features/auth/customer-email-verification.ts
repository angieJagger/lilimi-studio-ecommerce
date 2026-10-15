import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AuthApiService } from '../../core/auth/auth-api.service';

@Component({
  selector: 'app-customer-email-verification',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './customer-email-verification.html',
  styleUrl: './customer-email-verification.scss',
})
export class CustomerEmailVerification {
  private readonly api = inject(AuthApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private token: string | null = null;

  protected readonly transloco = inject(TranslocoService);
  protected readonly isSubmitting = signal(false);
  protected readonly verified = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly hasToken = signal(false);

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((fragment) => {
      this.token = new URLSearchParams(fragment ?? '').get('token');
      this.hasToken.set(this.token !== null && /^[A-Za-z0-9_-]{43}$/.test(this.token));

      this.verified.set(false);
      this.errorKey.set(this.hasToken() ? null : 'emailVerification.errors.invalidLink');
    });
  }

  protected verify(): void {
    if (this.isSubmitting() || this.verified() || !this.hasToken() || !this.token) {
      return;
    }

    this.isSubmitting.set(true);
    this.errorKey.set(null);

    this.api
      .verifyEmail(this.token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: () => {
          this.verified.set(true);
          this.token = null;
          this.hasToken.set(false);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 400) {
            this.token = null;
            this.hasToken.set(false);
            this.errorKey.set('emailVerification.errors.invalidLink');
            return;
          }

          this.errorKey.set('emailVerification.errors.failed');
        },
      });
  }
}
