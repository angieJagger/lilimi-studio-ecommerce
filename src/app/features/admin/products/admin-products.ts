import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminProductApiService } from './admin-product-api.service';
import type { AdminProductPage, AdminProductSummary } from './admin-product.model';

@Component({
  selector: 'app-admin-products',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './admin-products.html',
  styleUrl: './admin-products.scss',
})
export class AdminProducts {
  private readonly api = inject(AdminProductApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly transloco = inject(TranslocoService);

  protected readonly products = signal<AdminProductPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly isSaving = signal(false);
  protected readonly pendingProduct = signal<AdminProductSummary | null>(null);
  protected readonly visibilityErrorKey = signal<string | null>(null);

  private requestedPage = 0;

  constructor() {
    this.loadPage(0);
  }

  protected requestVisibilityChange(product: AdminProductSummary): void {
    if (this.isLoading() || this.isSaving() || this.pendingProduct() || this.visibilityErrorKey()) {
      return;
    }

    const currentProduct = this.products()?.items.find((item) => item.id === product.id);

    if (!currentProduct) {
      return;
    }

    this.pendingProduct.set(currentProduct);
  }

  protected cancelVisibilityChange(): void {
    if (this.isSaving()) {
      return;
    }

    this.pendingProduct.set(null);
  }

  protected confirmVisibilityChange(): void {
    const product = this.pendingProduct();

    if (!product || this.isLoading() || this.isSaving() || this.visibilityErrorKey()) {
      return;
    }

    this.isSaving.set(true);

    this.api
      .changeVisibility(product.id, {
        active: !product.active,
        expectedVersion: product.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          this.products.update((page) => {
            if (!page) {
              return page;
            }

            return {
              ...page,
              items: page.items.map((item) => (item.id === updated.id ? updated : item)),
            };
          });

          this.pendingProduct.set(null);
          this.visibilityErrorKey.set(null);
        },
        error: (error: unknown) => {
          this.pendingProduct.set(null);
          this.visibilityErrorKey.set(this.getVisibilityErrorKey(error));
        },
      });
  }

  protected previousPage(): void {
    const page = this.products();

    if (page && page.page > 0) {
      this.loadPage(page.page - 1);
    }
  }

  protected nextPage(): void {
    const page = this.products();

    if (page && page.page + 1 < page.totalPages) {
      this.loadPage(page.page + 1);
    }
  }

  protected retry(): void {
    this.loadPage(this.requestedPage);
  }

  private loadPage(page: number): void {
    if (this.isLoading() || this.isSaving() || page < 0) {
      return;
    }

    this.requestedPage = page;
    this.isLoading.set(true);
    this.errorKey.set(null);
    this.pendingProduct.set(null);
    this.visibilityErrorKey.set(null);

    this.api
      .getProducts(page)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false)),
      )
      .subscribe({
        next: (response) => {
          this.products.set(response);
        },
        error: (error: unknown) => {
          this.errorKey.set(this.getErrorKey(error));
        },
      });
  }

  private getVisibilityErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'adminOrders.errors.sessionExpired';
      }

      if (error.status === 403) {
        return 'adminOrders.errors.accessDenied';
      }

      if (error.status === 404) {
        return 'adminProducts.errors.notFound';
      }

      if (error.status === 409 && error.error?.code === 'PRODUCT_VERSION_CONFLICT') {
        return 'adminProducts.errors.versionConflict';
      }
    }

    return 'adminProducts.errors.visibilitySaveFailed';
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
      }
    }

    return 'adminProducts.errors.loadFailed';
  }
}
