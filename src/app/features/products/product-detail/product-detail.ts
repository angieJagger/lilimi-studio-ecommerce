import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { demoProducts } from '../products.data';
import { ProductCard } from '../../../shared/components/product-card/product-card';

@Component({
  imports: [RouterLink, TranslocoPipe, ProductCard],
  selector: 'app-product-detail',
  styleUrl: './product-detail.scss',
  templateUrl: './product-detail.html',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);

  private readonly params = toSignal(this.route.paramMap, {
    initialValue: this.route.snapshot.paramMap,
  });

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly product = computed(() => {
    const slug = this.params().get('slug');

    return demoProducts.find((product) => product.slug === slug);
  });

  protected readonly relatedProducts = computed(() => {
    const currentProduct = this.product();

    if (!currentProduct) {
      return [];
    }

    return demoProducts
      .filter(
        (product) =>
          product.category === currentProduct.category && product.id !== currentProduct.id,
      )
      .slice(0, 3);
  });

  protected readonly formattedPrice = computed(() => {
    const product = this.product();

    if (!product) {
      return '';
    }

    return new Intl.NumberFormat(this.language(), {
      style: 'currency',
      currency: 'PLN',
    }).format(product.priceInGrosz / 100);
  });
}
