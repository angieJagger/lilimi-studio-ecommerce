import { computed, inject, Injectable } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, map, of, startWith, switchMap, timeout } from 'rxjs';

import { ProductApiService } from './product-api.service';
import { mapApiProductToProduct } from './product-api.mapper';
import type { Product } from './product.model';

type CatalogState =
  { status: 'loading' } | { status: 'ready'; products: readonly Product[] } | { status: 'error' };

@Injectable({
  providedIn: 'root',
})
export class ProductCatalogService {
  private readonly productApi = inject(ProductApiService);
  private readonly reloadRequests = new Subject<void>();

  readonly state = toSignal(
    this.reloadRequests.pipe(
      startWith(undefined),
      switchMap(() =>
        this.productApi.getProducts().pipe(
          timeout(10_000),
          map((products): CatalogState => ({
            status: 'ready',
            products: products.map(mapApiProductToProduct),
          })),
          catchError(() => of<CatalogState>({ status: 'error' })),
          startWith<CatalogState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as CatalogState },
  );

  readonly products = computed(() => {
    const state = this.state();

    return state.status === 'ready' ? state.products : [];
  });

  readonly productsById = computed(
    () => new Map(this.products().map((product) => [product.id, product])),
  );

  reload(): void {
    this.reloadRequests.next();
  }
}
