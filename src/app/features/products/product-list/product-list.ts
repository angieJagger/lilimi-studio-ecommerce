import { Component, computed, inject, signal, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { demoProducts } from '../products.data';
import { ProductCard } from '../../../shared/components/product-card/product-card';

type ProductSort = 'default' | 'price-asc' | 'price-desc';
@Component({
  imports: [TranslocoPipe, ProductCard, RouterLink],
  selector: 'app-product-list',
  styleUrl: './product-list.scss',
  templateUrl: './product-list.html',
})
export class ProductList {
  protected readonly filtersOpen = signal(false);
  protected toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly transloco = inject(TranslocoService);
  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );
  private readonly queryParams = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  protected readonly category = computed(() => this.queryParams().get('category'));
  protected readonly sort = computed<ProductSort>(() => {
    const value = this.queryParams().get('sort');
    return value === 'price-asc' || value === 'price-desc' ? value : 'default';
  });

  protected readonly products = computed(() => {
    const category = this.category();

    const filteredProducts =
      category === 'embroidery-patterns' || category === 'embroidered-products'
        ? demoProducts.filter((product) => product.category === category)
        : demoProducts;

    switch (this.sort()) {
      case 'price-asc':
        return [...filteredProducts].sort(
          (first, second) => first.priceInGrosz - second.priceInGrosz,
        );

      case 'price-desc':
        return [...filteredProducts].sort(
          (first, second) => second.priceInGrosz - first.priceInGrosz,
        );

      default:
        return filteredProducts;
    }
  });

  private readonly pageSize = 6;

  protected readonly visibleLimit = linkedSignal({
    source: () => ({
      category: this.category(),
      sort: this.sort(),
    }),
    computation: (): number => this.pageSize,
  });

  protected readonly visibleProducts = computed(() =>
    this.products().slice(0, this.visibleLimit()),
  );

  protected readonly hasMoreProducts = computed(
    () => this.visibleProducts().length < this.products().length,
  );

  protected showMore(): void {
    this.visibleLimit.update((limit) => Math.min(limit + this.pageSize, this.products().length));
  }

  protected readonly titleKey = computed(() => {
    switch (this.category()) {
      case 'embroidery-patterns':
        return 'nav.patterns';

      case 'embroidered-products':
        return 'nav.embroideredProducts';

      default:
        return 'products.title';
    }
  });

  protected changeSort(value: string): void {
    const sort: ProductSort = value === 'price-asc' || value === 'price-desc' ? value : 'default';

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        sort: sort === 'default' ? null : sort,
      },
      queryParamsHandling: 'merge',
    });
  }
}
