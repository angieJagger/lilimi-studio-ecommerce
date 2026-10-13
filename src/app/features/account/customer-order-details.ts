import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
} from 'rxjs';
import { CustomerOrderApiService } from './customer-order-api.service';
import type { CustomerOrderDetails as OrderDetails } from './customer-order.model';

@Component({
  selector: 'app-customer-order-details',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './customer-order-details.html',
  styleUrl: './customer-order-details.scss',
})
export class CustomerOrderDetails {
  private readonly api = inject(CustomerOrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
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

  protected retry(): void {
    if (!this.isLoading() && !this.sessionExpired()) {
      this.reload.next();
    }
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
