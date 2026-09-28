import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ProductCard } from '../../../shared/components/product-card/product-card';
import { CartService } from '../../cart/cart.service';
import { SweatshirtConfigurator } from '../sweatshirt-configurator/sweatshirt-configurator';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
  timeout,
} from 'rxjs';
import { ProductApiService } from '../product-api.service';
import { mapApiProductToProduct } from '../product-api.mapper';
import type { Product } from '../product.model';

type ProductDetailState =
  | { status: 'loading' }
  | { status: 'ready'; product: Product }
  | { status: 'not-found' }
  | { status: 'error' };

@Component({
  imports: [RouterLink, TranslocoPipe, ProductCard, SweatshirtConfigurator],
  selector: 'app-product-detail',
  styleUrl: './product-detail.scss',
  templateUrl: './product-detail.html',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly cart = inject(CartService);

  private readonly productApi = inject(ProductApiService);
  private readonly reloadRequests = new Subject<void>();

  protected readonly detailState = toSignal(
    combineLatest([
      this.route.paramMap.pipe(
        map((params) => params.get('slug')),
        distinctUntilChanged(),
      ),
      this.reloadRequests.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([slug]) => {
        if (!slug) {
          return of<ProductDetailState>({ status: 'not-found' });
        }

        return this.productApi.getProduct(slug).pipe(
          timeout(10_000),
          map((response): ProductDetailState => ({
            status: 'ready',
            product: mapApiProductToProduct(response),
          })),
          catchError((error: unknown) =>
            of<ProductDetailState>({
              status:
                error instanceof HttpErrorResponse && error.status === 404 ? 'not-found' : 'error',
            }),
          ),
          startWith<ProductDetailState>({ status: 'loading' }),
        );
      }),
    ),
    { initialValue: { status: 'loading' } as ProductDetailState },
  );

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly product = computed(() => {
    const state = this.detailState();

    return state.status === 'ready' ? state.product : undefined;
  });

  protected reloadProduct(): void {
    this.reloadRequests.next();
  }

  protected readonly isInCart = computed(() => {
    const product = this.product();

    return product ? this.cart.patterns().some((item) => item.id === product.id) : false;
  });

  protected addToCart(): void {
    const product = this.product();

    if (product?.category === 'embroidery-patterns' && product.priceType === 'fixed') {
      this.cart.addPattern(product);
    }
  }

  private readonly catalogProducts = toSignal(
    this.productApi.getProducts().pipe(
      timeout(10_000),
      map((products) => products.map(mapApiProductToProduct)),
      catchError(() => of<Product[]>([])),
    ),
    { initialValue: [] as Product[] },
  );

  protected readonly relatedProducts = computed(() => {
    const currentProduct = this.product();

    if (!currentProduct) {
      return [];
    }

        return this.catalogProducts()
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
