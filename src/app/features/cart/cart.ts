import { ProductImage } from '../../shared/components/product-image/product-image';
import type { ProductImage as ImageModel } from '../products/product.model';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CartService } from './cart.service';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslocoPipe, ProductImage],
  templateUrl: './cart.html',
  styleUrl: './cart.scss',
})
export class Cart {
  private readonly transloco = inject(TranslocoService);

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

  protected primaryImage(images: readonly ImageModel[] | undefined): ImageModel | null {
    return images?.find((image) => image.primaryImage) ?? images?.[0] ?? null;
  }

  protected formatPrice(priceInGrosz: number | null): string {
    return priceInGrosz === null ? '—' : this.priceFormatter().format(priceInGrosz / 100);
  }
}
