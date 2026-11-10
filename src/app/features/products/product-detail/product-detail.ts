import { Component, computed, DestroyRef, inject, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ProductGallery } from './product-gallery';
import { ProductCard } from '../../../shared/components/product-card/product-card';
import { CartService } from '../../cart/cart.service';
import { SweatshirtConfigurator } from '../sweatshirt-configurator/sweatshirt-configurator';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  finalize,
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
  imports: [RouterLink, TranslocoPipe, ProductCard, SweatshirtConfigurator, ProductGallery],
  selector: 'app-product-detail',
  styleUrl: './product-detail.scss',
  templateUrl: './product-detail.html',
})
export class ProductDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly transloco = inject(TranslocoService);
  private readonly cart = inject(CartService);
  private readonly destroyRef = inject(DestroyRef);

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

  protected readonly cartReady = () => this.cart.isReady();

  protected readonly physicalFeedback = linkedSignal({
    source: () => this.product()?.id,
    computation: (): { key: string; max?: number } | null => null,
  });
  protected readonly isCheckingPhysicalStock = signal(false);

  protected addPhysical(event: SubmitEvent, form: HTMLFormElement): void {
    event.preventDefault();
    const product = this.product();
    if (
      product?.category !== 'embroidered-products' ||
      product.productType !== 'physical' ||
      !this.cartReady() ||
      this.isCheckingPhysicalStock()
    ) {
      return;
    }

    const options = Object.fromEntries(
      Array.from(new FormData(form), ([name, value]) => [name, String(value)]),
    );
    // Options share a single stock count for this product.
    const quantityInCart = () =>
      this.cart.physicalItems()
        .filter((item) => item.productId === product.id)
        .reduce((total, item) => total + item.quantity, 0);

    const requested = quantityInCart() + 1;
    this.physicalFeedback.set(null);
    this.isCheckingPhysicalStock.set(true);
    this.productApi.checkQuantity(product.slug, requested).pipe(
      timeout(10_000),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.isCheckingPhysicalStock.set(false)),
    ).subscribe({
      next: (limit) => {
        if (this.product() !== product) return;

        if (limit) {
          this.physicalFeedback.set({
            key: limit.maxQuantity === 0 ? 'cart.stockUnavailable' : 'cart.stockLimit',
            max: limit.maxQuantity,
          });
          return;
        }

        if (quantityInCart() + 1 > requested) {
          this.physicalFeedback.set({ key: 'cart.stockCheckFailed' });
          return;
        }

        const added = this.cart.addPhysical(product, options);
        this.physicalFeedback.set({
          key: added ? 'productDetail.physicalAdded' : 'productDetail.physicalAddFailed',
        });
      },
      error: (error: unknown) => {
        if (this.product() !== product) return;

        this.physicalFeedback.set({
          key: error instanceof HttpErrorResponse && error.status === 404
            ? 'cart.stockUnavailable'
            : 'cart.stockCheckFailed',
        });
      },
    });
  }

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
