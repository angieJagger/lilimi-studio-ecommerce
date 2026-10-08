import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import type { AdminOrderDetails as OrderDetails, AdminOrderStatus } from './admin-order.model';

@Component({
  selector: 'app-admin-order-details',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-order-details.html',
  styleUrl: './admin-order-details.scss',
})
export class AdminOrderDetails {
  private readonly api = inject(AdminOrderApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();

  protected readonly transloco = inject(TranslocoService);

  protected readonly order = signal<OrderDetails | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly isSaving = signal(false);
  protected readonly pendingStatus = signal<AdminOrderStatus | null>(null);
  protected readonly statusErrorKey = signal<string | null>(null);

  protected readonly availableStatuses = computed<readonly AdminOrderStatus[]>(() => {
    switch (this.order()?.status) {
      case 'new':
        return ['processing', 'cancelled'];
      case 'processing':
        return ['completed', 'cancelled'];
      default:
        return [];
    }
  });

  constructor() {
    combineLatest([this.route.paramMap, this.reload.pipe(startWith(undefined))])
      .pipe(
        switchMap(([params]) =>
          defer(() => {
            const id = params.get('id');

            this.order.set(null);
            this.errorKey.set(null);
            this.pendingStatus.set(null);
            this.statusErrorKey.set(null);

            if (!id) {
              this.isLoading.set(false);
              this.errorKey.set('adminOrderDetails.errors.notFound');
              return EMPTY;
            }

            this.isLoading.set(true);

            return this.api.getOrder(id).pipe(
              tap((order) => this.order.set(order)),
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

  protected retry(): void {
    if (!this.isLoading() && !this.isSaving()) {
      this.reload.next();
    }
  }

  protected requestStatusChange(status: AdminOrderStatus): void {
    if (
      this.isSaving() ||
      this.isLoading() ||
      this.statusErrorKey() ||
      !this.availableStatuses().includes(status)
    ) {
      return;
    }

    this.pendingStatus.set(status);
  }

  protected cancelStatusChange(): void {
    if (!this.isSaving()) {
      this.pendingStatus.set(null);
    }
  }

  protected confirmStatusChange(): void {
    const current = this.order();
    const nextStatus = this.pendingStatus();

    if (
      !current ||
      !nextStatus ||
      this.isSaving() ||
      this.isLoading() ||
      this.statusErrorKey() ||
      !this.availableStatuses().includes(nextStatus)
    ) {
      return;
    }

    this.isSaving.set(true);

    this.api
      .changeStatus(current.id, {
        status: nextStatus,
        expectedVersion: current.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          if (this.order()?.id !== current.id) {
            return;
          }

          this.order.set(updated);
          this.pendingStatus.set(null);
        },
        error: (error: unknown) => {
          if (this.order()?.id !== current.id) {
            return;
          }

          this.pendingStatus.set(null);
          this.statusErrorKey.set(this.getStatusErrorKey(error));
        },
      });
  }

  private getStatusErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }

      if (error.status === 409) {
        return error.error?.code === 'ORDER_STATUS_TRANSITION_INVALID'
          ? 'adminOrderDetails.errors.transitionInvalid'
          : 'adminOrderDetails.errors.versionConflict';
      }
    }

    return 'adminOrderDetails.errors.statusSaveFailed';
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

  protected formatColor(value: string | null): string {
    if (!value) {
      return '—';
    }

    const knownColors = [
      'black',
      'white',
      'gray',
      'grey',
      'beige',
      'navy',
      'blue',
      'green',
      'pink',
      'red',
      'brown',
    ];

    return knownColors.includes(value)
      ? this.transloco.translate(`adminOrderDetails.colors.${value}`)
      : value;
  }

  private getLocale(): string {
    return this.transloco.getActiveLang() === 'en' ? 'en-GB' : 'pl-PL';
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400 || error.status === 404) {
        return 'adminOrderDetails.errors.notFound';
      }

      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminOrderDetails.errors.loadFailed';
  }
}
