import { Component, computed, input } from '@angular/core';
import { ProductImage } from '../product-image/product-image';
import type { ProductImage as ImageModel } from '../../../features/products/product.model';

@Component({
  selector: 'app-product-card',
  imports: [ProductImage],
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  readonly slug = input('');
  readonly images = input<readonly ImageModel[]>([]);
  protected readonly primaryImage = computed(
    () => this.images().find((image) => image.primaryImage) ?? null,
  );

  readonly productName = input.required<string>();
  readonly typeLabel = input.required<string>();
  readonly priceInGrosz = input.required<number>();

  readonly description = input('');
  readonly locale = input('pl');

  readonly pricePrefix = input('');

  protected readonly formattedPrice = computed(() =>
    new Intl.NumberFormat(this.locale(), {
      style: 'currency',
      currency: 'PLN',
    }).format(this.priceInGrosz() / 100),
  );
}
