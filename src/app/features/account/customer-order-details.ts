import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  map,
  of,
  startWith,
  Subject,
  switchMap,
  finalize,
} from 'rxjs';
import { CustomerOrderApiService } from './customer-order-api.service';
import { PaymentApiService } from '../checkout/payment-api.service';
import { PaymentRedirectService } from '../checkout/payment-redirect.service';
import type { CustomerOrderDetails as OrderDetails } from './customer-order.model';
import { DigitalOrderDownloads } from '../digital-downloads/digital-order-downloads';

@Component({
  selector: 'app-customer-order-details',
  imports: [TranslocoPipe, RouterLink, DigitalOrderDownloads],
  templateUrl: './customer-order-details.html',
  styleUrl: './customer-order-details.scss',
})
export class CustomerOrderDetails {
  private readonly api = inject(CustomerOrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private readonly payments = inject(PaymentApiService);
  private readonly paymentRedirect = inject(PaymentRedirectService);
  private readonly router = inject(Router);

  private paymentKey: {
    orderId: string;
    key: string;
  } | null = null;

  protected readonly isResumingPayment = signal(false);
  protected readonly paymentErrorKey = signal<string | null>(null);

  protected readonly transloco = inject(TranslocoService);
  protected readonly order = signal<OrderDetails | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly sessionExpired = signal(false);

  constructor() {
    combineLatest([
      this.route.paramMap.pipe(
        map((params) => params.get('id')),
        distinctUntilChanged(),
      ),
      this.reload.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([id]) => {
          this.order.set(null);
          this.errorKey.set(null);
          this.sessionExpired.set(false);

          if (!id) {
            this.isLoading.set(false);
            this.errorKey.set('customerOrderDetails.errors.notFound');
            return of(null);
          }

          this.isLoading.set(true);

          return this.api.getOrder(id).pipe(
            catchError((error: unknown) => {
              this.errorKey.set(this.getErrorKey(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((order) => {
        this.order.set(order);
        this.isLoading.set(false);
      });
  }

  protected hasDigitalItems(details: OrderDetails): boolean {
    return details.items.some((item) => item.kind === 'digital');
  }

  protected retry(): void {
    if (!this.isLoading() && !this.sessionExpired()) {
      this.reload.next();
    }
  }

  protected resumePayment(): void {
    const order = this.order();

    if (!order || !order.payment?.canPay || this.isLoading() || this.isResumingPayment()) {
      return;
    }

    this.paymentErrorKey.set(null);

    try {
      if (this.paymentKey?.orderId !== order.id) {
        this.paymentKey = {
          orderId: order.id,
          key: crypto.randomUUID(),
        };
      }
    } catch {
      this.paymentErrorKey.set('orderPayment.error');
      return;
    }

    const key = this.paymentKey!.key;

    this.isResumingPayment.set(true);

    this.payments
      .resumeCheckout(order.id, key)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isResumingPayment.set(false)),
      )
      .subscribe({
        next: (result) => {
          // Do not redirect if another order is now displayed.
          if (this.order()?.id !== order.id) {
            return;
          }

          if (result.status === 'expired' || result.status === 'failed') {
            const closedStatus = result.status;
            this.paymentKey = null;

            this.order.update((current) =>
              current?.id === order.id
                ? {
                    ...current,
                    payment: {
                      status: closedStatus,
                      canPay: true,
                    },
                  }
                : current,
            );

            this.paymentErrorKey.set('orderPayment.sessionClosed');
            return;
          }

          if (result.status === 'pending' && result.checkoutUrl) {
            try {
              this.paymentRedirect.redirect(result.checkoutUrl);
            } catch {
              this.paymentErrorKey.set('orderPayment.error');
            }

            return;
          }

          void this.router.navigate(
            ['/', this.transloco.getActiveLang(), 'checkout', 'payment-result'],
            {
              queryParams: {
                attempt: result.paymentAttemptId,
              },
            },
          );
        },
        error: (error: unknown) => {
          if (this.order()?.id !== order.id) {
            return;
          }

          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.retry();
            this.paymentErrorKey.set('orderPayment.stateChanged');
            return;
          }

          this.paymentErrorKey.set(
            error instanceof HttpErrorResponse && error.status === 0
              ? 'orderPayment.connectionError'
              : 'orderPayment.error',
          );
        },
      });
  }

  protected formatDate(value: string): string {
    return new Intl.DateTimeFormat(this.getLocale(), {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  protected formatPrice(value: number): string {
    return new Intl.NumberFormat(this.getLocale(), {
      style: 'currency',
      currency: 'PLN',
    }).format(value / 100);
  }

  private getLocale(): string {
    return this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        this.sessionExpired.set(true);
        return 'customerOrderDetails.errors.sessionExpired';
      }

      if (error.status === 404 || error.status === 400) {
        return 'customerOrderDetails.errors.notFound';
      }

      if (error.status === 403) {
        return 'customerOrderDetails.errors.accessDenied';
      }
    }

    return 'customerOrderDetails.errors.loadFailed';
  }
}
