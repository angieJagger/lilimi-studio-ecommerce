import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { CustomerOrderApiService } from './customer-order-api.service';
import type { CustomerOrderPage } from './customer-order.model';

@Component({
  selector: 'app-customer-orders',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './customer-orders.html',
  styleUrl: './customer-orders.scss',
})
export class CustomerOrders {
  private readonly api = inject(CustomerOrderApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly transloco = inject(TranslocoService);

  protected readonly orders = signal<CustomerOrderPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly sessionExpired = signal(false);

  constructor() {
    this.loadPage(0);
  }

  protected loadPage(page: number): void {
    if (this.isLoading() || this.sessionExpired() || page < 0) {
      return;
    }

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.api
      .getOrders(page)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (response) => {
          this.orders.set(response);
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            this.orders.set(null);
            this.sessionExpired.set(true);
            this.errorKey.set('customerOrders.errors.sessionExpired');
            return;
          }

          if (error instanceof HttpErrorResponse && error.status === 403) {
            this.orders.set(null);
            this.errorKey.set('customerOrders.errors.accessDenied');
            return;
          }

          this.errorKey.set('customerOrders.errors.loadFailed');
        },
      });
  }

  protected previousPage(): void {
    const current = this.orders();

    if (current && current.page > 0) {
      this.loadPage(current.page - 1);
    }
  }

  protected nextPage(): void {
    const current = this.orders();

    if (current && current.page + 1 < current.totalPages) {
      this.loadPage(current.page + 1);
    }
  }

  protected retry(): void {
    this.loadPage(this.orders()?.page ?? 0);
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
}
