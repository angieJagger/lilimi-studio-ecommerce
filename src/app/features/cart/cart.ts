import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CartService } from './cart.service';
import { demoProducts } from '../products/products.data';
import { dragonEmbroideryOptions } from '../products/embroidered-product-options';
import { getCartItemKey } from './cart-item-key';

@Component({
  selector: 'app-cart',
  imports: [RouterLink, TranslocoPipe],
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

  protected readonly sweatshirtLines = computed(() =>
    this.cart.sweatshirts().flatMap((item) => {
      const product = demoProducts.find((product) => product.id === item.productId);

      const pattern = demoProducts.find((product) => product.id === item.patternId);

      const embroidery = dragonEmbroideryOptions.find(
        (option) => option.id === item.configuration.embroideryOptionId,
      );

      if (!product || !pattern || !embroidery) {
        return [];
      }

      return [
        {
          key: getCartItemKey(item),
          item,
          product,
          pattern,
          embroidery,
          unitPriceInGrosz: this.cart.getSweatshirtUnitPrice(item),
        },
      ];
    }),
  );

  protected readonly priceFormatter = computed(
    () =>
      new Intl.NumberFormat(this.language(), {
        style: 'currency',
        currency: 'PLN',
      }),
  );
}
