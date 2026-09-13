import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-product-card',
  imports: [],
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
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
