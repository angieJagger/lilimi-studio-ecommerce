import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  catchError,
  EMPTY,
  exhaustMap,
  finalize,
  type Observable,
  startWith,
  Subject,
  switchMap,
  take,
  takeWhile,
  tap,
  timeout,
  timer,
} from 'rxjs';
import {
  PaymentApiService,
  type PaymentStatus,
  type PaymentStatusResult,
} from './payment-api.service';

@Component({
  selector: 'app-payment-result',
  imports: [RouterLink, TranslocoPipe],
  template: `
    <section class="checkout" aria-labelledby="payment-result-title">
      <div class="container">
        <h1 id="payment-result-title" class="heading-1">
          {{ 'paymentResult.title' | transloco }}
        </h1>

        @if (errorKey(); as error) {
          <p role="alert">{{ error | transloco }}</p>
        } @else {
          <output>{{ statusKey() | transloco }}</output>
        }

        @if (cancelled() && !terminal()) {
          <p>{{ 'paymentResult.cancelled' | transloco }}</p>
        }

        @if (isChecking()) {
          <p>{{ 'paymentResult.checking' | transloco }}</p>
        }

        @if (validAttempt() && !isChecking() && !terminal()) {
          <button class="button button--primary" type="button" (click)="refresh()">
            {{ 'paymentResult.refresh' | transloco }}
          </button>
        }

        <p>{{ 'paymentResult.notice' | transloco }}</p>

        <div class="checkout__actions">
          <a class="button button--secondary" [routerLink]="['/', language(), 'account/orders']">
            {{ 'paymentResult.orders' | transloco }}
          </a>

          <a class="button button--secondary" [routerLink]="['/', language(), 'products']">
            {{ 'paymentResult.products' | transloco }}
          </a>
        </div>
      </div>
    </section>
  `,
  styleUrl: './checkout.scss',
})
export class PaymentResult {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly payments = inject(PaymentApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshRequests = new Subject<void>();

  protected readonly status = signal<PaymentStatus | null>(null);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly isChecking = signal(false);
  protected readonly validAttempt = signal(false);

  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed(() => (this.activeLanguage() === 'pl' ? 'pl' : 'en'));

  protected readonly cancelled = computed(() => this.queryParams().get('cancelled') === 'true');

  protected readonly terminal = computed(
    () =>
      this.status() === 'succeeded' || this.status() === 'failed' || this.status() === 'expired',
  );

  protected readonly statusKey = computed(() => {
    switch (this.status()) {
      case 'succeeded':
        return 'paymentResult.succeeded';
      case 'failed':
        return 'paymentResult.failed';
      case 'expired':
        return 'paymentResult.expired';
      default:
        return 'paymentResult.pending';
    }
  });

  constructor() {
    this.route.queryParamMap
      .pipe(
        switchMap((params) =>
          this.refreshRequests.pipe(
            startWith(undefined),
            switchMap(() => this.checkStatus(params.get('attempt'))),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected refresh(): void {
    if (!this.isChecking() && !this.terminal()) {
      this.refreshRequests.next();
    }
  }

  private checkStatus(attemptId: string | null): Observable<PaymentStatusResult> {
    this.status.set(null);
    this.errorKey.set(null);

    const valid =
      attemptId !== null &&
      /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(attemptId);

    this.validAttempt.set(valid);

    if (!valid || attemptId === null) {
      this.isChecking.set(false);
      this.errorKey.set('paymentResult.invalidLink');
      return EMPTY;
    }

    this.isChecking.set(true);

    return timer(0, 3000).pipe(
      exhaustMap(() => this.payments.getStatus(attemptId).pipe(timeout(10000))),
      take(21),
      tap((result) => this.status.set(result.status)),
      takeWhile((result) => result.status === 'created' || result.status === 'pending', true),
      catchError((error: unknown) => {
        const unavailable =
          error instanceof HttpErrorResponse &&
          (error.status === 401 || error.status === 403 || error.status === 404);

        this.errorKey.set(
          unavailable ? 'paymentResult.accessError' : 'paymentResult.connectionError',
        );

        return EMPTY;
      }),
      finalize(() => this.isChecking.set(false)),
    );
  }
}
