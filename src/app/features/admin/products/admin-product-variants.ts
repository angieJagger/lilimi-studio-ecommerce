import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe } from '@angular/common';
import { form, FormField, required, validate } from '@angular/forms/signals';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminProductApiService } from './admin-product-api.service';
import type { AdminGarmentVariant, AdminGarmentVariantPage } from './admin-product.model';

@Component({
  selector: 'app-admin-product-variants',
  imports: [TranslocoPipe, CurrencyPipe, FormField],
  templateUrl: './admin-product-variants.html',
  styleUrl: './admin-product-variants.scss',
})
export class AdminProductVariants {
  readonly productId = input.required<string>();

  private readonly api = inject(AdminProductApiService);
  private readonly destroyRef = inject(DestroyRef);

  private requestedPage = 0;
  private requestNumber = 0;

  protected readonly variants = signal<AdminGarmentVariantPage | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);

  protected readonly selectedVariant = signal<AdminGarmentVariant | null>(null);
  protected readonly isSaving = signal(false);
  protected readonly saveErrorKey = signal<string | null>(null);

  protected readonly editModel = signal({
    amount: '',
    active: false,
  });

  protected readonly editForm = form(this.editModel, (path) => {
    required(path.amount, {
      message: 'adminProducts.price.errors.required',
    });

    validate(path.amount, ({ value }) => {
      if (value() === '') {
        return undefined;
      }

      return this.parsePrice(value()) === null
        ? {
            kind: 'invalidPrice',
            message: 'adminProducts.price.errors.invalid',
          }
        : undefined;
    });
  });

  constructor() {
    effect(() => {
      const productId = this.productId();

      this.selectedVariant.set(null);
      this.saveErrorKey.set(null);
      this.variants.set(null);
      this.loadPage(0, productId);
    });
  }

  protected editVariant(variant: AdminGarmentVariant): void {
    if (this.isLoading() || this.isSaving() || this.selectedVariant()) {
      return;
    }

    this.saveErrorKey.set(null);
    this.editForm().reset({
      amount: this.formatPrice(variant.priceInGrosz),
      active: variant.active,
    });
    this.selectedVariant.set(variant);
  }

  protected cancelEdit(): void {
    if (this.isSaving() || this.saveErrorKey()) {
      return;
    }

    this.selectedVariant.set(null);
  }

  protected saveVariant(event: Event): void {
    event.preventDefault();

    const variant = this.selectedVariant();
    const productId = this.productId();

    if (!variant || this.isSaving() || this.saveErrorKey()) {
      return;
    }

    this.editForm().markAsTouched();

    if (!this.editForm().valid()) {
      this.editForm.amount().focusBoundControl();
      return;
    }

    const priceInGrosz = this.parsePrice(this.editModel().amount);

    if (priceInGrosz === null) {
      return;
    }

    this.isSaving.set(true);

    this.api
      .updateVariant(productId, variant.id, {
        priceInGrosz,
        active: this.editModel().active,
        expectedVersion: variant.version,
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: (updated) => {
          if (this.productId() !== productId || this.selectedVariant()?.id !== variant.id) {
            return;
          }

          this.variants.update((page) =>
            page
              ? {
                  ...page,
                  items: page.items.map((item) => (item.id === updated.id ? updated : item)),
                }
              : page,
          );

          this.selectedVariant.set(null);
        },
        error: (error: unknown) => {
          if (this.productId() !== productId || this.selectedVariant()?.id !== variant.id) {
            return;
          }

          this.saveErrorKey.set(this.getSaveErrorKey(error));
        },
      });
  }

  private parsePrice(input: string): number | null {
    const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(input.trim());

    if (!match) {
      return null;
    }

    const grosz = Number(match[1]) * 100 + Number((match[2] ?? '').padEnd(2, '0'));

    return Number.isSafeInteger(grosz) && grosz > 0 && grosz <= 2_147_483_647 ? grosz : null;
  }

  private formatPrice(grosz: number): string {
    return `${Math.floor(grosz / 100)}.${String(grosz % 100).padStart(2, '0')}`;
  }

  private getSaveErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
          return 'adminProducts.variants.errors.invalidRequest';
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
        case 404:
          return 'adminProducts.variants.errors.notFound';
        case 409:
          return 'adminProducts.variants.errors.versionConflict';
      }
    }

    return 'adminProducts.variants.errors.saveFailed';
  }

  protected previousPage(): void {
    const page = this.variants();

    if (page && page.page > 0 && !this.isLoading() && !this.isSaving() && !this.selectedVariant()) {
      this.loadPage(page.page - 1);
    }
  }

  protected nextPage(): void {
    const page = this.variants();

    if (
      page &&
      page.page + 1 < page.totalPages &&
      !this.isLoading() &&
      !this.isSaving() &&
      !this.selectedVariant()
    ) {
      this.loadPage(page.page + 1);
    }
  }

  protected retry(): void {
    if (this.isLoading() || this.isSaving()) {
      return;
    }

    this.selectedVariant.set(null);
    this.saveErrorKey.set(null);
    this.loadPage(this.requestedPage);
  }

  private loadPage(page: number, productId = this.productId()): void {
    const requestNumber = ++this.requestNumber;

    this.requestedPage = page;
    this.isLoading.set(true);
    this.errorKey.set(null);

    this.api
      .getVariants(productId, page)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestNumber === this.requestNumber) {
            this.isLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (response) => {
          if (requestNumber !== this.requestNumber) {
            return;
          }

          this.variants.set(response);
        },
        error: (error: unknown) => {
          if (requestNumber !== this.requestNumber) {
            return;
          }

          this.errorKey.set(this.getErrorKey(error));
        },
      });
  }

  private getErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 401:
          return 'adminOrders.errors.sessionExpired';
        case 403:
          return 'adminOrders.errors.accessDenied';
        case 404:
          return 'adminProducts.errors.notFound';
      }
    }

    return 'adminProducts.variants.errors.loadFailed';
  }
}
