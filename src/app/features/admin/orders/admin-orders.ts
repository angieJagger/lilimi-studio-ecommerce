import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminOrderApiService } from './admin-order-api.service';
import type { AdminOrderPage } from './admin-order.model';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-orders',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-orders.html',
  styleUrl: './admin-orders.scss',
})
export class AdminOrders {
  private readonly api = inject(AdminOrderApiService);
  protected readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly orders = signal<AdminOrderPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  constructor() {
    this.loadPage(0);
  }

  protected loadPage(page: number): void {
    if (this.isLoading() || page < 0) {
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
          this.errorKey.set(this.getErrorKey(error));
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

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminOrders.errors.loadFailed';
  }
}
