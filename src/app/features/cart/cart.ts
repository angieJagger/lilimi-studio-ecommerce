import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ProductImage } from '../../shared/components/product-image/product-image';
import type { ProductImage as ImageModel } from '../products/product.model';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ProductApiService } from '../products/product-api.service';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CartService } from './cart.service';
import { MAX_CART_ITEM_QUANTITY } from './cart-item.model';
import type { CartItem } from './cart-item.model';

type ShippableItem = Exclude<CartItem, { kind: 'digital' }>;

@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslocoPipe, ProductImage],
  templateUrl: './cart.html',
  styleUrl: './cart.scss',
})
export class Cart {
  private readonly transloco = inject(TranslocoService);
  private readonly productApi = inject(ProductApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly stockLimits = signal(new Map<string, { max: number | null; rejected: boolean }>());
  private readonly pendingChecks = signal(new Set<string>());

  protected readonly maxItemQuantity = MAX_CART_ITEM_QUANTITY;

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly cart = inject(CartService);

  protected readonly priceFormatter = computed(
    () =>
      new Intl.NumberFormat(this.language(), {
        style: 'currency',
        currency: 'PLN',
      }),
  );

  protected readonly sweatshirtLines = this.cart.sweatshirtLines;
  protected readonly canCheckout = computed(() =>
    this.cart.pricingStatus() === 'ready' && this.pendingChecks().size === 0 &&
    ![...this.stockLimits()].some(([productId, limit]) =>
      limit.max !== null && this.productQuantity(productId) > limit.max,
    ),
  );

  protected changeQuantity(key: string, item: ShippableItem, slug: string | undefined, quantity: number, variantId?: string): void {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > this.maxItemQuantity) return;

    const productId = variantId ?? item.productId;
    if (item.kind === 'sweatshirt' && !variantId && quantity > item.quantity) return;
    if (quantity <= item.quantity) {
      this.updateQuantity(key, item, quantity);
      const limit = this.stockLimits().get(productId);
      if (limit) this.setStockLimit(productId, limit.max, false);
      return;
    }
    if (!slug || this.isCheckingQuantity(productId)) return;

    const currentTotal = this.productQuantity(productId);
    const requested = currentTotal - item.quantity + quantity;
    this.pendingChecks.update((pending) => new Set(pending).add(productId));

    // Checking one additional unit detects the point where the + button should stop.
    this.productApi.checkQuantity(slug, requested + 1, variantId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pendingChecks.update((pending) =>
        new Set([...pending].filter((id) => id !== productId)),
      )),
    ).subscribe({
      next: (result) => {
        if (this.productQuantity(productId) !== currentTotal) return;

        const max = result?.maxQuantity ?? null;
        const rejected = max !== null && requested > max;
        this.setStockLimit(productId, max, rejected);
        if (!rejected) this.updateQuantity(key, item, quantity);
      },
      error: (error: unknown) => {
        if (this.productQuantity(productId) !== currentTotal) return;
        this.setStockLimit(productId, error instanceof HttpErrorResponse && error.status === 404 ? 0 : null, true);
      },
    });
  }

  protected changeTypedQuantity(
    key: string, item: ShippableItem, slug: string | undefined, input: HTMLInputElement, variantId?: string,
  ): void {
    const quantity = input.validity.valid ? input.valueAsNumber : item.quantity;
    input.value = String(item.quantity);
    this.changeQuantity(key, item, slug, quantity, variantId);
  }

  protected isCheckingQuantity(productId: string): boolean {
    return this.pendingChecks().has(productId);
  }

  protected canIncreaseQuantity(item: ShippableItem, variantId?: string): boolean {
    if (item.kind === 'sweatshirt' && !variantId) return false;
    const key = variantId ?? item.productId;
    const max = this.stockLimits().get(key)?.max;
    return item.quantity < this.maxItemQuantity && !this.isCheckingQuantity(key)
      && (max == null || this.productQuantity(key) < max);
  }

  protected quantityFeedback(productId: string): { key: string; max: number | null } | null {
    if (this.productQuantity(productId) === 0) return null;
    const limit = this.stockLimits().get(productId);
    if (!limit || (!limit.rejected && (limit.max === null || this.productQuantity(productId) <= limit.max))) {
      return null;
    }
    if (limit.max === null) return { key: 'cart.stockCheckFailed', max: null };
    return { key: limit.max === 0 ? 'cart.stockUnavailable' : 'cart.stockLimit', max: limit.max };
  }

  private setStockLimit(productId: string, max: number | null, rejected: boolean): void {
    this.stockLimits.update((limits) => new Map(limits).set(productId, { max, rejected }));
  }

  private updateQuantity(key: string, item: ShippableItem, quantity: number): void {
    if (item.kind === 'sweatshirt') {
      this.cart.updateSweatshirtQuantity(key, quantity);
    } else {
      this.cart.updatePhysicalQuantity(key, quantity);
    }
  }

  private productQuantity(productId: string): number {
    const variantQuantity = this.cart.sweatshirtLines().find((line) => line.variantId === productId)?.item.quantity;
    return variantQuantity ?? this.cart.physicalItems()
      .reduce((total, item) => item.productId === productId ? total + item.quantity : total, 0);
  }

  protected primaryImage(images: readonly ImageModel[] | undefined): ImageModel | null {
    return images?.find((image) => image.primaryImage) ?? images?.[0] ?? null;
  }

  protected formatPrice(priceInGrosz: number | null): string {
    return priceInGrosz === null ? '—' : this.priceFormatter().format(priceInGrosz / 100);
  }
}
