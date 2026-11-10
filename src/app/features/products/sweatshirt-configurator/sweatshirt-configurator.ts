import { Component, computed, DestroyRef, inject, linkedSignal, input, effect, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MAX_CART_ITEM_QUANTITY } from '../../cart/cart-item.model';

import {
  EmbroideryOptionId,
  garmentColors,
  GarmentColor,
  garmentFits,
  GarmentFit,
  garmentSizes
} from '../embroidered-product-options';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ProductApiService } from '../product-api.service';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { CartService } from '../../cart/cart.service';
import { SweatshirtVariantsService } from '../sweatshirt-variants.service';
import type { EmbroideryOption } from '../embroidered-product-options';
import type { EmbroideredProduct } from '../product.model';
import type { VariantsSource } from '../sweatshirt-variants.service';
import { ProductCatalogService } from '../product-catalog.service';


@Component({
  selector: 'app-sweatshirt-configurator',
  imports: [TranslocoPipe, RouterLink, FormsModule],
  templateUrl: './sweatshirt-configurator.html',
  styleUrl: './sweatshirt-configurator.scss',
})
export class SweatshirtConfigurator {
  private readonly transloco = inject(TranslocoService);
  readonly product = input.required<EmbroideredProduct>();
  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  private readonly cart = inject(CartService);
  private readonly productApi = inject(ProductApiService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly isCheckingStock = signal(false);
  protected readonly rejectedStock = linkedSignal({
    source: () => this.selectedVariant()?.id,
    computation: (): number | null => null,
  });
  protected readonly stockCheckFailed = linkedSignal({
    source: () => this.selectedVariant()?.id,
    computation: () => false,
  });
  private readonly catalog = inject(ProductCatalogService);
  private readonly variantsService = inject(SweatshirtVariantsService);
  private readonly variantsSource = signal<VariantsSource | null>(null);
  protected readonly variantsState = computed(
    () => this.variantsSource()?.state() ?? { status: 'loading' as const },
  );
  protected readonly variants = computed(() => this.variantsSource()?.variants() ?? []);

  constructor() {
    effect(() => {
      const slug = this.product().slug;
      const source = untracked(() => this.variantsService.forProduct(slug));
      this.variantsSource.set(source);
    });
  }

  protected reloadVariants(): void {
    this.variantsSource()?.reload();
  }
  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly maxItemQuantity = MAX_CART_ITEM_QUANTITY;

  protected readonly quantityLimitReached = computed(() => {
    const variant = this.selectedVariant();

    if (!variant) {
      return false;
    }

    return this.cart
      .sweatshirts()
      .some(
        (item) =>
          item.productId === this.product().id &&
          item.patternId === variant.patternId &&
          item.configuration.fit === variant.fit &&
          item.configuration.size === variant.size &&
          item.configuration.color === variant.color &&
          item.configuration.embroideryOptionId === variant.embroideryOptionId &&
          item.quantity >= MAX_CART_ITEM_QUANTITY,
      );
  });

  protected readonly addedCount = linkedSignal({
    source: () => ({
      productId: this.product().id,
      patternId: this.selectedPatternId(),
      fit: this.selectedFit(),
      size: this.selectedSize(),
      color: this.selectedColor(),
      embroidery: this.selectedEmbroidery(),
    }),
    computation: (): number => 0,
  });

  protected addToCart(): void {
    const variant = this.selectedVariant();
    if (!variant || this.isCheckingStock() || this.quantityLimitReached()) return;

    const product = this.product();
    const quantity = this.cart.sweatshirtLines().find((line) => line.variantId === variant.id)?.item.quantity ?? 0;
    this.rejectedStock.set(null);
    this.stockCheckFailed.set(false);
    this.isCheckingStock.set(true);

    this.productApi.checkQuantity(product.slug, quantity + 1, variant.id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isCheckingStock.set(false)),
    ).subscribe({
      next: (limit) => {
        if (this.selectedVariant()?.id !== variant.id) return;
        if (limit) {
          this.rejectedStock.set(limit.maxQuantity);
          return;
        }

        if (this.cart.addSweatshirt(product.id, variant.patternId, {
          fit: variant.fit,
          size: variant.size,
          color: variant.color,
          embroideryOptionId: variant.embroideryOptionId,
        })) {
          this.addedCount.update((count) => count + 1);
        }
      },
      error: (error: unknown) => {
        if (this.selectedVariant()?.id !== variant.id) return;
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.rejectedStock.set(0);
        } else {
          this.stockCheckFailed.set(true);
        }
      },
    });
  }

  protected readonly patternIds = computed(() => [
    ...new Set(this.variants().map((variant) => variant.patternId)),
  ]);

  protected readonly selectedPatternId = linkedSignal<readonly string[], string | undefined>({
    source: this.patternIds,
    computation: (ids, previous) => {
      const previousId = previous?.value;

      return previousId !== undefined && ids.includes(previousId) ? previousId : ids[0];
    },
  });

  private readonly patternVariants = computed(() =>
    this.variants().filter((variant) => variant.patternId === this.selectedPatternId()),
  );

  protected readonly fits = computed(() =>
    garmentFits.filter((fit) => this.patternVariants().some((variant) => variant.fit === fit)),
  );

  protected readonly colors = computed(() =>
    garmentColors.filter((color) =>
      this.patternVariants().some(
        (variant) => variant.fit === this.selectedFit() && variant.color === color,
      ),
    ),
  );

  protected readonly embroideryOptions = computed(() => {
    const matchingVariants = this.patternVariants().filter(
      (variant) => variant.fit === this.selectedFit() && variant.color === this.selectedColor(),
    );

    return [
      ...new Map(
        matchingVariants.map((variant) => [
          variant.embroideryOptionId,
          {
            id: variant.embroideryOptionId,
            widthMm: variant.widthMm,
            heightMm: variant.heightMm,
            placement: variant.placement,
          },
        ]),
      ).values(),
    ];
  });

  protected readonly selectedFit = linkedSignal({
    source: this.fits,
    computation: (fits): GarmentFit | undefined => (fits.includes('women') ? 'women' : fits[0]),
  });

  protected readonly selectedColor = linkedSignal<
    readonly GarmentColor[],
    GarmentColor | undefined
  >({
    source: this.colors,
    computation: (colors, previous) => {
      const previousColor = previous?.value;

      if (previousColor && colors.includes(previousColor)) {
        return previousColor;
      }

      return colors.includes('black') ? 'black' : colors[0];
    },
  });

  protected readonly selectedEmbroidery = linkedSignal<
    readonly EmbroideryOption[],
    EmbroideryOptionId | undefined
  >({
    source: this.embroideryOptions,
    computation: (options, previous) => {
      const previousOption = options.find((option) => option.id === previous?.value);

      return (
        previousOption?.id ??
        options.find((option) => option.id === 'small-front')?.id ??
        options[0]?.id
      );
    },
  });

  protected readonly availableSizes = computed(() => {
    const fit = this.selectedFit();

    if (!fit) {
      return [];
    }

    return garmentSizes[fit].filter((size) =>
      this.patternVariants().some(
        (variant) =>
          variant.fit === fit &&
          variant.color === this.selectedColor() &&
          variant.embroideryOptionId === this.selectedEmbroidery() &&
          variant.size === size,
      ),
    );
  });

  protected readonly selectedSize = linkedSignal<readonly string[], string>({
    source: this.availableSizes,
    computation: (sizes, previous) => {
      const previousSize = previous?.value;

      return previousSize && sizes.includes(previousSize) ? previousSize : '';
    },
  });

  protected readonly selectedVariant = computed(() =>
    this.patternVariants().find(
      (variant) =>
        variant.fit === this.selectedFit() &&
        variant.color === this.selectedColor() &&
        variant.embroideryOptionId === this.selectedEmbroidery() &&
        variant.size === this.selectedSize(),
    ),
  );

  protected readonly priceInGrosz = computed(() => this.selectedVariant()?.priceInGrosz ?? null);

  protected readonly formattedPrice = computed(() => {
    const price = this.priceInGrosz();

    return price === null
      ? '—'
      : new Intl.NumberFormat(this.activeLanguage(), {
          style: 'currency',
          currency: 'PLN',
        }).format(price / 100);
  });

  protected readonly configurationComplete = computed(() => this.selectedVariant() !== undefined);

  protected changeFit(value: string): void {
    const fit = this.fits().find((item) => item === value);

    if (fit) {
      this.selectedFit.set(fit);
    }
  }

  protected changePattern(patternId: string): void {
    if (this.patternIds().includes(patternId)) {
      this.selectedPatternId.set(patternId);
    }
  }

  protected patternName(patternId: string): string {
    return this.catalog.productsById().get(patternId)?.name[this.language()] ?? patternId;
  }

  protected changeSize(value: string): void {
    if (this.availableSizes().includes(value)) {
      this.selectedSize.set(value);
    }
  }

  protected changeColor(value: string): void {
    const color = this.colors().find((item) => item === value);

    if (color) {
      this.selectedColor.set(color);
    }
  }

  protected changeEmbroidery(value: string): void {
    const option = this.embroideryOptions().find((item) => item.id === value);

    if (option) {
      this.selectedEmbroidery.set(option.id);
    }
  }
}
