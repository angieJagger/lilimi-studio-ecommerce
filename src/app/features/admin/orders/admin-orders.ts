import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  catchError,
  combineLatest,
  defer,
  EMPTY,
  finalize,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';
import { AdminOrderApiService } from './admin-order-api.service';
import type {
  AdminOrderFilters,
  AdminOrderPage,
  AdminOrderStatus,
  AdminOrderView,
} from './admin-order.model';

@Component({
  selector: 'app-admin-orders',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-orders.html',
  styleUrl: './admin-orders.scss',
})
export class AdminOrders {
  private readonly api = inject(AdminOrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly orders = signal<AdminOrderPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly filterErrorKey = signal<string | null>(null);
  protected readonly filters = signal<AdminOrderFilters>({
    view: 'active',
  });

  protected readonly statuses: readonly AdminOrderStatus[] = [
    'new',
    'processing',
    'completed',
    'cancelled',
  ];

  constructor() {
    combineLatest([this.route.queryParamMap, this.reload.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const filters = this.readFilters(params);
            const page = this.readPage(params);

            this.filters.set(filters);
            this.orders.set(null);
            this.errorKey.set(null);
            this.filterErrorKey.set(null);
            this.isLoading.set(true);

            return this.api.getOrders(page, 20, filters).pipe(
              tap((response) => this.orders.set(response)),
              catchError((error: unknown) => {
                this.errorKey.set(this.getErrorKey(error));
                return EMPTY;
              }),
              finalize(() => this.isLoading.set(false)),
            );
          }),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  protected selectView(view: AdminOrderView): void {
    this.navigate({
      ...this.filters(),
      view,
      status: undefined,
    });
  }

  protected applyFilters(
  status: string,
  dateFrom: string,
  dateTo: string,
  search: string,
): void {
  const phrase = search.trim();

  if (phrase.length > 254) {
    this.filterErrorKey.set('adminOrders.filters.searchTooLong');
    return;
  }

  // A new phrase starts a search across all orders.
  if (phrase && phrase !== (this.filters().search ?? '')) {
    this.filterErrorKey.set(null);
    this.navigate({
      view: 'all',
      search: phrase,
    });
    return;
  }

  if (dateFrom && dateTo && dateFrom > dateTo) {
    this.filterErrorKey.set('adminOrders.filters.invalidDateRange');
    return;
  }

  this.filterErrorKey.set(null);

  this.navigate({
    ...this.filters(),
    status: this.readStatus(status),
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    search: phrase || undefined,
  });
}

  protected resetFilters(): void {
    this.filterErrorKey.set(null);
    this.navigate({ view: this.filters().view ?? 'active' });
  }

  protected previousPage(): void {
    const current = this.orders();

    if (current && current.page > 0) {
      this.navigate(this.filters(), current.page - 1);
    }
  }

  protected nextPage(): void {
    const current = this.orders();

    if (current && current.page + 1 < current.totalPages) {
      this.navigate(this.filters(), current.page + 1);
    }
  }

  protected retry(): void {
    if (!this.isLoading()) {
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

  private navigate(filters: AdminOrderFilters, page = 0): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        view: filters.view ?? 'active',
        status: filters.status ?? null,
        dateFrom: filters.dateFrom ?? null,
        dateTo: filters.dateTo ?? null,
        search: filters.search ?? null,
        page: page > 0 ? page : null,
      },
    });
  }

  private readFilters(params: ParamMap): AdminOrderFilters {
    const value = params.get('view');
    const view: AdminOrderView = value === 'archive' || value === 'all' ? value : 'active';

    return {
      view,
      status: this.readStatus(params.get('status')),
      dateFrom: params.get('dateFrom') || undefined,
      dateTo: params.get('dateTo') || undefined,
      search: params.get('search')?.trim() || undefined,
    };
  }

  private readStatus(value: string | null): AdminOrderStatus | undefined {
    return this.statuses.find((status) => status === value);
  }

  private readPage(params: ParamMap): number {
    const value = Number(params.get('page') ?? '0');

    return Number.isSafeInteger(value) && value >= 0 ? value : 0;
  }

  private getLocale(): string {
    return this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) {
        return 'adminOrders.filters.invalidParameters';
      }

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
