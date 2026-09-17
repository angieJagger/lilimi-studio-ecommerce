import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import {
  dragonEmbroideryOptions,
  EmbroideryOptionId,
  garmentColors,
  GarmentColor,
  garmentFits,
  GarmentFit,
  garmentSizes,
  sweatshirtPricesInGrosz,
} from '../embroidered-product-options';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { CartService } from '../../cart/cart.service';


@Component({
  selector: 'app-sweatshirt-configurator',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './sweatshirt-configurator.html',
  styleUrl: './sweatshirt-configurator.scss',
})
export class SweatshirtConfigurator {
  private readonly transloco = inject(TranslocoService);

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  private readonly cart = inject(CartService);

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
    if (!this.configurationComplete()) {
      return;
    }

    const added = this.cart.addSweatshirt({
      fit: this.selectedFit(),
      size: this.selectedSize(),
      color: this.selectedColor(),
      embroideryOptionId: this.selectedEmbroidery(),
    });

    if (added) {
      this.addedCount.update((count) => count + 1);
    }
  }

  protected readonly fits = garmentFits;
  protected readonly colors = garmentColors;
  protected readonly embroideryOptions = dragonEmbroideryOptions;

  protected readonly selectedFit = signal<GarmentFit>('women');
  protected readonly selectedColor = signal<GarmentColor>('black');
  protected readonly selectedEmbroidery = signal<EmbroideryOptionId>('small-front');

  protected readonly availableSizes = computed(() => garmentSizes[this.selectedFit()]);

  protected readonly selectedSize = linkedSignal({
    source: this.selectedFit,
    computation: (): string => '',
  });

  protected readonly priceInGrosz = computed(
    () => sweatshirtPricesInGrosz[this.selectedFit()][this.selectedEmbroidery()],
  );

  protected readonly formattedPrice = computed(() =>
    new Intl.NumberFormat(this.activeLanguage(), {
      style: 'currency',
      currency: 'PLN',
    }).format(this.priceInGrosz() / 100),
  );

  protected readonly configurationComplete = computed(() =>
    this.availableSizes().includes(this.selectedSize()),
  );

  protected changeFit(value: string): void {
    const fit = this.fits.find((item) => item === value);

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
    const color = this.colors.find((item) => item === value);

    if (color) {
      this.selectedColor.set(color);
    }
  }

  protected changeEmbroidery(value: string): void {
    const option = this.embroideryOptions.find((item) => item.id === value);

    if (option) {
      this.selectedEmbroidery.set(option.id);
    }
  }
}
