import { Component, computed, inject, linkedSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  EmbroideryOptionId,
  garmentColors,
  GarmentColor,
  garmentFits,
  GarmentFit,
  garmentSizes
} from '../embroidered-product-options';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { CartService } from '../../cart/cart.service';
import { SweatshirtVariantsService } from '../sweatshirt-variants.service';
import type { EmbroideryOption } from '../embroidered-product-options';


@Component({
  selector: 'app-sweatshirt-configurator',
  imports: [TranslocoPipe, RouterLink, FormsModule],
  templateUrl: './sweatshirt-configurator.html',
  styleUrl: './sweatshirt-configurator.scss',
})
export class SweatshirtConfigurator {
  private readonly transloco = inject(TranslocoService);

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  private readonly cart = inject(CartService);

  private readonly variantsService = inject(SweatshirtVariantsService);

  protected readonly variantsState = this.variantsService.state;
  protected readonly variants = this.variantsService.variants;

  protected reloadVariants(): void {
    this.variantsService.reload();
  }

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly addedCount = linkedSignal({
    source: () => ({
      fit: this.selectedFit(),
      size: this.selectedSize(),
      color: this.selectedColor(),
      embroidery: this.selectedEmbroidery(),
    }),
    computation: (): number => 0,
  });

  protected addToCart(): void {
    const variant = this.selectedVariant();

    if (!variant) {
      return;
    }

    const added = this.cart.addSweatshirt({
      fit: variant.fit,
      size: variant.size,
      color: variant.color,
      embroideryOptionId: variant.embroideryOptionId,
    });

    if (added) {
      this.addedCount.update((count) => count + 1);
    }
  }

  private readonly dragonVariants = computed(() =>
    this.variants().filter((variant) => variant.patternId === 'pattern-001'),
  );

  protected readonly fits = computed(() =>
    garmentFits.filter((fit) => this.dragonVariants().some((variant) => variant.fit === fit)),
  );

  protected readonly colors = computed(() =>
    garmentColors.filter((color) =>
      this.dragonVariants().some(
        (variant) => variant.fit === this.selectedFit() && variant.color === color,
      ),
    ),
  );

  protected readonly embroideryOptions = computed(() => {
    const matchingVariants = this.dragonVariants().filter(
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
        const previousOption = options.find(
          (option) => option.id === previous?.value,
        );
  
        return previousOption?.id ??
          options.find((option) => option.id === 'small-front')?.id ??
          options[0]?.id;
      },
    });

  protected readonly availableSizes = computed(() => {
    const fit = this.selectedFit();

    if (!fit) {
      return [];
    }

    return garmentSizes[fit].filter((size) =>
      this.dragonVariants().some(
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
    this.dragonVariants().find(
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
