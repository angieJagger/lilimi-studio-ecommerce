import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, ElementRef, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { AdminProductApiService } from './admin-product-api.service';
import { AdminProductVariants } from './admin-product-variants';
import type {
  AdminProductPage,
  AdminProductSummary,
  CreateProductDraftRequest,
} from './admin-product.model';

interface ProductImagePreview {
  readonly url: string;
  readonly name: string;
  readonly failed: boolean;
}

@Component({
  selector: 'app-admin-products',
  imports: [TranslocoPipe, RouterLink, AdminProductVariants],
  templateUrl: './admin-products.html',
  styleUrl: './admin-products.scss',
})
export class AdminProducts {
  private readonly api = inject(AdminProductApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly transloco = inject(TranslocoService);

  protected readonly products = signal<AdminProductPage | null>(null);
  protected readonly expandedStockProductId = signal<string | null>(null);
  protected readonly isLoading = signal(false);
  protected readonly errorKey = signal<string | null>(null);
  protected readonly isSaving = signal(false);
  protected readonly pendingProduct = signal<AdminProductSummary | null>(null);
  protected readonly visibilityErrorKey = signal<string | null>(null);
  protected readonly showProductTypePicker = signal(false);
  protected readonly newProductType = signal<'digital' | 'physical' | null>(null);
  protected readonly hasVariants = signal<boolean | null>(null);
  protected readonly variantAttributeIds = signal<readonly number[]>([0]);
  protected readonly isCreatingDraft = signal(false);
  protected readonly draftErrorKey = signal<string | null>(null);
  protected readonly imagePreviews = signal<readonly ProductImagePreview[]>([]);
  private readonly imageInput = viewChild<ElementRef<HTMLInputElement>>('productImages');

  private requestedPage = 0;
  private nextVariantAttributeId = 1;

  constructor() {
    effect((onCleanup) => {
      if (this.imageInput()) {
        onCleanup(() => this.clearImagePreviews());
      }
    });
    this.loadPage(0);
  }

  protected previewImages(input: HTMLInputElement): void {
    this.clearImagePreviews();
    const files = Array.from(input.files ?? []);
    if (files.length > 10) {
      this.draftErrorKey.set('adminProducts.create.tooManyImages');
      return;
    }

    if (this.draftErrorKey() === 'adminProducts.create.tooManyImages') {
      this.draftErrorKey.set(null);
    }
    this.imagePreviews.set(files.map((file) => ({
      url: URL.createObjectURL(file),
      name: file.name,
      failed: false,
    })));
  }

  protected markImagePreviewFailed(url: string): void {
    this.imagePreviews.update((previews) =>
      previews.map((preview) => preview.url === url ? { ...preview, failed: true } : preview),
    );
  }

  private clearImagePreviews(): void {
    for (const preview of untracked(this.imagePreviews)) URL.revokeObjectURL(preview.url);
    this.imagePreviews.set([]);
  }

  protected addVariantAttribute(): void {
    this.variantAttributeIds.update((ids) => [...ids, this.nextVariantAttributeId++]);
  }

  protected removeVariantAttribute(id: number): void {
    this.variantAttributeIds.update((ids) => ids.filter((attributeId) => attributeId !== id));
  }

  protected createDraft(event: SubmitEvent, form: HTMLFormElement): void {
    event.preventDefault();

    const productType = this.newProductType();
    const hasVariants = this.hasVariants();
    if (!productType || this.isCreatingDraft() || (productType === 'physical' && hasVariants === null)) {
      return;
    }

    const fields = new FormData(form);
    const field = (name: string) => String(fields.get(name) ?? '').trim();
    const request: CreateProductDraftRequest = {
      productType,
      pl: { name: field('namePl'), description: field('descriptionPl') },
      en: { name: field('nameEn'), description: field('descriptionEn') },
      productCode: field('productCode') || undefined,
    };

    const price = Math.round(Number(field('price').replace(',', '.')) * 100);
    if (!Number.isInteger(price) || price < 1 || price > 2_147_483_647) {
      this.draftErrorKey.set('adminProducts.create.invalidPrice');
      return;
    }

    const files = (name: string) =>
      Array.from((form.elements.namedItem(name) as HTMLInputElement | null)?.files ?? []);
    const images = files('images');
    if (images.length > 10) {
      this.draftErrorKey.set('adminProducts.create.tooManyImages');
      return;
    }

    if (productType === 'physical') {
      const stock = field('stockQuantity');
      const stockQuantity = Number(stock);
      if (!stock || !Number.isInteger(stockQuantity) || stockQuantity < 0 || stockQuantity > 2_147_483_647) {
        this.draftErrorKey.set('adminProducts.create.invalidStock');
        return;
      }

      const measurement = (name: string) => {
        const value = field(name);
        return value ? Number(value.replace(',', '.')) : null;
      };
      const shippingPackage = {
        lengthCm: measurement('packageLength'),
        widthCm: measurement('packageWidth'),
        heightCm: measurement('packageHeight'),
        weightKg: measurement('packageWeight'),
      };
      const measurements = Object.values(shippingPackage);
      if (measurements.some((value) => value !== null && (!Number.isFinite(value) || value <= 0))) {
        this.draftErrorKey.set('adminProducts.create.invalidPackage');
        return;
      }

      this.submitDraft({
        ...request,
        stockQuantity,
        physicalConfiguration: {
          ...(measurements.some((value) => value !== null) ? { shippingPackage } : {}),
          category: field('physicalCategory') || null,
          priceInGrosz: price,
          variantAttributes: hasVariants
            ? this.variantAttributeIds().map((id) => ({
                name: field(`attribute-name-${id}`),
                values: field(`attribute-values-${id}`).split(',').map((value) => value.trim()),
              }))
            : [],
        },
      }, images);
      return;
    }

    const [digitalPackage] = files('digitalPackage');
    if (!digitalPackage) {
      this.draftErrorKey.set('adminProducts.create.packageRequired');
      return;
    }

    this.submitDraft({ ...request, digitalPriceInGrosz: price }, images, digitalPackage);
  }

  private submitDraft(
    request: CreateProductDraftRequest,
    images: readonly File[],
    digitalPackage?: File,
  ): void {
    this.draftErrorKey.set(null);
    this.isCreatingDraft.set(true);

    this.api
      .createProduct(request, images, digitalPackage)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isCreatingDraft.set(false)),
      )
      .subscribe({
        next: (product) => {
          void this.router.navigate([product.id, 'edit'], { relativeTo: this.route });
        },
        error: (error: unknown) => {
          this.draftErrorKey.set(this.getDraftErrorKey(error));
        },
      });
  }

  private getDraftErrorKey(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      switch (error.error?.code) {
        case 'PRODUCT_CODE_CONFLICT':
          return 'adminProducts.create.codeTaken';
        case 'PRODUCT_SLUG_CONFLICT':
          return 'adminProducts.create.slugTaken';
      }
    }

    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 400:
          return error.error?.code === 'DIGITAL_PACKAGE_INVALID'
            ? 'adminDigitalPackages.errors.invalidPackage'
            : 'adminProducts.create.invalid';
        case 415:
        case 422:
          return 'adminProducts.create.invalid';
        case 413:
          return 'adminProducts.create.filesTooLarge';
      }
    }

    return 'adminProducts.create.failed';
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
    this.expandedStockProductId.set(null);
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

      if (error.status === 409 && error.error?.code === 'PHYSICAL_PRODUCT_NOT_PUBLISHABLE') {
        return 'adminProducts.errors.physicalProductNotPublishable';
      }

      if (error.status === 409 && error.error?.code === 'SWEATSHIRT_NOT_READY') {
        return 'adminProducts.errors.sweatshirtNotReady';
      }

      if (error.status === 409 && error.error?.code === 'DIGITAL_PRODUCT_NOT_READY') {
        return 'adminProducts.errors.digitalProductNotReady';
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
